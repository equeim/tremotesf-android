// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import okhttp3.Request
import okhttp3.Response
import org.equeim.tremotesf.rpc.requests.FreeSpaceResponseArguments
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.NotNormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.RpcRequestBody
import org.equeim.tremotesf.rpc.requests.RpcResponse
import org.equeim.tremotesf.rpc.requests.SERVER_VERSION_REQUEST
import org.equeim.tremotesf.rpc.requests.ServerVersionResponseArguments
import timber.log.Timber
import java.net.HttpURLConnection

open class RpcClient(protected val coroutineScope: CoroutineScope) {
    private val connectionConfiguration = MutableStateFlow<Result<ConnectionConfiguration>?>(null)
    internal fun getConnectionConfiguration(): StateFlow<Result<ConnectionConfiguration>?> = connectionConfiguration

    internal val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            contextual(NormalizedRpcPath.Serializer(::serverCapabilities))
            contextual(NotNormalizedRpcPath.Serializer(::serverCapabilities))
        }
    }

    @Volatile
    private var sessionId: String? = null

    private val serverCapabilitiesResult = MutableStateFlow<Result<ServerCapabilities>?>(null)
    val serverCapabilities: ServerCapabilities? get() = serverCapabilitiesResult.value?.getOrNull()
    val serverCapabilitiesFlow: Flow<ServerCapabilities?>
        get() = serverCapabilitiesResult
            .map { it?.getOrNull() }
            .distinctUntilChanged()

    val shouldConnectToServer = MutableStateFlow(true)

    init {
        coroutineScope.launch {
            shouldConnectToServer.collect {
                if (!it) {
                    connectionConfiguration.value?.getOrNull()?.httpClient?.apply {
                        dispatcher.cancelAll()
                        connectionPool.evictAll()
                    }
                }
            }
        }
    }

    @Synchronized
    fun setConnectionConfiguration(server: Server?) {
        Timber.d("setConnectionConfiguration() called with: server = $server")
        val newConnectionConfiguration = server?.let {
            try {
                Result.success(createConnectionConfiguration(it))
            } catch (e: Exception) {
                Timber.e(e, "Bad connection configuration")
                Result.failure(e)
            }
        }
        connectionConfiguration.value?.getOrNull()?.httpClient?.apply {
            dispatcher.cancelAll()
            dispatcher.executorService.shutdown()
            connectionPool.evictAll()
        }
        sessionId = null
        serverCapabilitiesResult.value = null
        connectionConfiguration.value = newConnectionConfiguration
    }

    /**
     * @param arguments Request arguments
     * @throws RpcRequestError
     */
    internal suspend inline fun <reified ResponseArguments : Any, reified RequestArguments> performRequest(
        method: RpcMethod,
        arguments: RequestArguments,
        callerContext: String? = null,
    ): RpcResponse<ResponseArguments> {
        return performRequest<ResponseArguments>(RpcRequestBody(method, arguments, json), callerContext)
    }

    /**
     * @param requestBody Request body
     * @throws RpcRequestError
     */
    internal suspend inline fun <reified ResponseArguments : Any> performRequest(
        requestBody: RpcRequestBody,
        callerContext: String? = null,
    ): RpcResponse<ResponseArguments> {
        return performRequest(
            requestBody = requestBody,
            responseArgumentsSerializer = serializer<ResponseArguments>(),
            callerContext = callerContext
        )
    }

    private suspend fun <ResponseArguments : Any> performRequest(
        requestBody: RpcRequestBody,
        responseArgumentsSerializer: KSerializer<ResponseArguments>,
        callerContext: String?,
    ): RpcResponse<ResponseArguments> {
        val context = RpcRequestContext(requestBody.method, callerContext)
        checkServerCapabilities(force = false, context)
        return try {
            performRequestImpl(requestBody, responseArgumentsSerializer, context)
        } catch (e: RpcRequestError.UnsuccessfulHttpStatusCode) {
            processUnsuccessfulHttpStatusCode(e)
            Timber.e("Session id changed, checking server capabilities")
            checkServerCapabilities(force = true, context)
            Timber.e("Retrying request after session id change")
            performRequestImpl(requestBody, responseArgumentsSerializer, context)
        }
    }

    private suspend fun <ResponseArguments : Any> performRequestImpl(
        requestBody: RpcRequestBody,
        responseArgumentsSerializer: KSerializer<ResponseArguments>,
        context: RpcRequestContext,
    ): RpcResponse<ResponseArguments> {
        Timber.d("Performing RPC request with $context")
        val configuration =
            (connectionConfiguration.value ?: throw RpcRequestError.NoConnectionConfiguration()).getOrElse {
                throw RpcRequestError.BadConnectionConfiguration(it as Exception)
            }
        if (!shouldConnectToServer.value) {
            throw RpcRequestError.ConnectionDisabled()
        }
        val call = configuration.httpClient.newCall(
            Request.Builder()
                .url(configuration.url)
                .post(requestBody)
                .tag(RpcRequestContext::class.java, context)
                .apply {
                    configuration.credentials?.let { addHeader(AUTHORIZATION_HEADER, it) }
                    sessionId?.let { addHeader(SESSION_ID_HEADER, it) }
                }.build()
        )
        return suspendCancellableCoroutine { continuation ->
            call.enqueue(OkHttpCallback(continuation, json, responseArgumentsSerializer, context))
            continuation.invokeOnCancellation {
                call.cancel()
            }
        }
    }

    private val checkingServerCapabilitiesMutex = Mutex()

    internal suspend fun checkServerCapabilities(force: Boolean, context: RpcRequestContext): ServerCapabilities {
        if (checkingServerCapabilitiesMutex.isLocked) {
            Timber.d("Waiting until server capabilities are checked before RPC request with $context")
        }
        checkingServerCapabilitiesMutex.withLock {
            if (!force) {
                serverCapabilitiesResult.value?.let { return it.getOrThrow() }
            }
            Timber.d("Checking server capabilities before RPC request with $context")
            return try {
                actuallyCheckServerCapabilities()
            } catch (e: RpcRequestError.UnsuccessfulHttpStatusCode) {
                processUnsuccessfulHttpStatusCode(e)
                Timber.e("Retrying request after session id change before request with $context")
                actuallyCheckServerCapabilities()
            }
        }
    }

    private suspend fun actuallyCheckServerCapabilities(): ServerCapabilities {
        val serverVersionResponse = performRequestImpl<ServerVersionResponseArguments>(
            SERVER_VERSION_REQUEST,
            serializer(),
            RpcRequestContext(SERVER_VERSION_REQUEST.method, "checkServerCapabilities")
        )
        Timber.d("Server version response is $serverVersionResponse")
        if (!serverVersionResponse.arguments.isSupported) {
            Timber.e("Unsupported server version ${serverVersionResponse.arguments.version}")
            val error = RpcRequestError.UnsupportedServerVersion(
                version = serverVersionResponse.arguments.version,
                response = serverVersionResponse.httpResponse,
                requestHeaders = serverVersionResponse.requestHeaders,
            )
            serverCapabilitiesResult.value = Result.failure(error)
            throw error
        }
        val serverOs = try {
            val freeSpaceResponse = performRequestImpl<FreeSpaceResponseArguments>(
                UNIX_ROOT_FREE_SPACE_REQUEST,
                serializer(),
                RpcRequestContext(UNIX_ROOT_FREE_SPACE_REQUEST.method, "checkServerCapabilities")
            )
            Timber.d("Free space response for Unix root directory is $freeSpaceResponse")
            ServerCapabilities.ServerOs.UnixLike
        } catch (e: RpcRequestError.UnsuccessfulResultField) {
            Timber.d("Free space request for Unix root directory failed with result ${e.result}")
            ServerCapabilities.ServerOs.Windows
        }
        Timber.d("Assuming that server's OS is $serverOs")
        val capabilities = ServerCapabilities(serverVersionResponse.arguments.rpcVersion, serverOs)
        Timber.d("Successfully checked server capabilities: $capabilities")
        serverCapabilitiesResult.value = Result.success(capabilities)
        return capabilities
    }

    private fun processUnsuccessfulHttpStatusCode(e: RpcRequestError.UnsuccessfulHttpStatusCode) {
        if (e.response.code == HttpURLConnection.HTTP_CONFLICT) {
            sessionId = e.response.headers[SESSION_ID_HEADER]
        } else {
            throw e
        }
    }
}

internal class RpcRequestContext(private val method: RpcMethod, private val callerContext: String?) {
    override fun toString(): String = if (callerContext != null) {
        "method '$method' and context '$callerContext'"
    } else {
        "method '$method'"
    }
}

internal val Response.status: String
    get() = if (message.isNotEmpty()) {
        "$code $message"
    } else {
        code.toString()
    }
