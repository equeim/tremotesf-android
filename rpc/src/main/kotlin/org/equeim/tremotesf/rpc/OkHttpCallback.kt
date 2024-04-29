// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response
import org.equeim.tremotesf.rpc.requests.BaseRpcResponse
import org.equeim.tremotesf.rpc.requests.isSuccessful
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpCallback<RpcResponseT : BaseRpcResponse>(
    private val continuation: CancellableContinuation<RpcResponseT>,
    private val json: Json,
    private val responseBodySerializer: KSerializer<RpcResponseT>,
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
            if (body == null || body.contentLength() == 0L) {
                resumeWithException(
                    RpcRequestError.DeserializationError(
                        response = response,
                        requestHeaders = requestHeaders,
                        cause = SerializationException("Response does not have a body")
                    )
                )
                return
            }
            val rpcResponseOrError = try {
                @OptIn(ExperimentalSerializationApi::class)
                Result.success(json.decodeFromBufferedSource(responseBodySerializer, body.source()))
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (!continuation.isActive) return
            rpcResponseOrError.onSuccess {
                if (it.isSuccessful) {
                    resume(it, response, requestHeaders)
                } else {
                    resumeWithException(
                        RpcRequestError.UnsuccessfulResultField(
                            result = it.result,
                            rawArguments = it.rawArguments,
                            response = response,
                            requestHeaders = requestHeaders
                        )
                    )
                }
            }.onFailure {
                resumeWithException(
                    when (it) {
                        is IOException -> it.toRpcRequestError(call, response, requestHeaders)
                        is SerializationException -> RpcRequestError.DeserializationError(response, requestHeaders, it)
                        is Exception -> RpcRequestError.UnexpectedError(response, requestHeaders, it)
                        else -> throw it
                    }
                )
            }
        }
    }

    private fun resume(rpcResponse: RpcResponseT, httpResponse: Response, requestHeaders: Headers) {
        val elapsed = SystemClock.elapsedRealtime() - startTimeMillis
        Timber.tag(RpcClient::class.simpleName!!).d("RPC request with method $context succeeded, took $elapsed ms")
        rpcResponse.httpResponse = httpResponse
        rpcResponse.requestHeaders = requestHeaders
        continuation.resume(rpcResponse)
    }

    private fun resumeWithException(error: RpcRequestError) {
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
