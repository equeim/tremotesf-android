// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response
import org.equeim.tremotesf.rpc.requests.RawRpcResponse
import org.equeim.tremotesf.rpc.requests.RpcResponse
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpCallback<ResponseArguments : Any>(
    private val continuation: CancellableContinuation<RpcResponse<ResponseArguments>>,
    private val json: Json,
    private val responseArgumentsSerializer: KSerializer<ResponseArguments>,
    private val context: RpcRequestContext,
) : Callback {
    private val startTimeMillis = SystemClock.elapsedRealtime()

    override fun onFailure(call: Call, e: IOException) {
        val requestHeaders = consumeRealRequestHeaders(context)
        if (continuation.isActive) {
            resumeWithException(e.toRpcRequestError(call, response = null, requestHeaders = requestHeaders))
        }
    }

    override fun onResponse(call: Call, response: Response) {
        val requestHeaders = checkNotNull(consumeRealRequestHeaders(context))
        response.body.use { body ->
            if (!continuation.isActive) return
            Timber.tag(TAG).log(
                if (response.isSuccessful) Log.DEBUG else Log.ERROR,
                "Received response headers for RPC request with $context: status is ${response.status}"
            )
            if (!response.isSuccessful) {
                resumeWithException(
                    when (response.code) {
                        HttpURLConnection.HTTP_UNAUTHORIZED -> RpcRequestError.AuthenticationError(
                            response,
                            requestHeaders
                        )

                        else -> RpcRequestError.UnsuccessfulHttpStatusCode(
                            response = response,
                            responseBody = body?.string(),
                            requestHeaders = requestHeaders
                        )
                    }
                )
                return
            }
            try {
                if (body == null || body.contentLength() == 0L) {
                    throw SerializationException("Response does not have a body")
                }
                @OptIn(ExperimentalSerializationApi::class)
                val rawRpcResponse = json.decodeFromBufferedSource<RawRpcResponse>(body.source())
                if (!rawRpcResponse.isSuccessful) {
                    resumeWithException(
                        RpcRequestError.UnsuccessfulResultField(
                            result = rawRpcResponse.result,
                            rawArguments = rawRpcResponse.arguments,
                            response = response,
                            requestHeaders = requestHeaders
                        )
                    )
                    return
                }
                val arguments = if (responseArgumentsSerializer == Unit.serializer()) {
                    @Suppress("UNCHECKED_CAST")
                    Unit as ResponseArguments
                } else {
                    if (rawRpcResponse.arguments == null) {
                        throw SerializationException("Response has no arguments")
                    }
                    json.decodeFromJsonElement(responseArgumentsSerializer, rawRpcResponse.arguments)
                }
                resume(RpcResponse(arguments, response, requestHeaders))
            } catch (e: Exception) {
                resumeWithException(
                    when (e) {
                        is IOException -> e.toRpcRequestError(call, response, requestHeaders)
                        is SerializationException -> RpcRequestError.DeserializationError(response, requestHeaders, e)
                        else -> RpcRequestError.UnexpectedError(response, requestHeaders, e)
                    }
                )
            }
        }
    }

    private fun resume(response: RpcResponse<ResponseArguments>) {
        if (!continuation.isActive) return
        val elapsed = SystemClock.elapsedRealtime() - startTimeMillis
        Timber.tag(RpcClient::class.simpleName!!).d("RPC request with method $context succeeded, took $elapsed ms")
        continuation.resume(response)
    }

    private fun resumeWithException(error: RpcRequestError) {
        if (!continuation.isActive) return
        val elapsed = SystemClock.elapsedRealtime() - startTimeMillis
        synchronized(Companion) {
            Timber.tag(RpcClient::class.simpleName!!).e(error, "RPC request with $context failed, took $elapsed ms")
            error.response?.let {
                Timber.tag(TAG).e("Response headers:")
                it.headers.logOnError()
            }
            (error as? RpcRequestError.UnsuccessfulHttpStatusCode)?.responseBody?.let {
                Timber.tag(TAG).e("Response body:\n$it")
            }
            error.requestHeaders?.let {
                Timber.tag(TAG).e("Request headers were:")
                it.logOnError()
            }
        }
        continuation.resumeWithException(error)
    }

    private companion object {
        const val TAG = "RpcClient"

        fun Headers.logOnError() {
            for (header in this) {
                val (name, value) = header.redactHeader()
                Timber.tag(TAG).e(" $name: $value")
            }
        }
    }
}
