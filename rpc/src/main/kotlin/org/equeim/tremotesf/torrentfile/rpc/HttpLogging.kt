package org.equeim.tremotesf.torrentfile.rpc

import android.util.Log
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import kotlin.time.Duration.Companion.nanoseconds

internal class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = checkNotNull(request.tag(RpcRequestContext::class.java))
        Timber.tag(TAG).d("Performing HTTP request for RPC request with $context")
        val startTime = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "HTTP request for RPC request with $context failed with $e")
            throw e
        }
        val elapsed = (System.nanoTime() - startTime).nanoseconds
        synchronized(this) {
            Timber.tag(TAG).log(
                if (response.isSuccessful) Log.DEBUG else Log.ERROR,
                "HTTP request for RPC request with $context completed with status ${response.status}, took $elapsed"
            )
            Timber.tag(TAG).d("Response headers:")
            response.headers.log()
        }
        return response
    }

    private companion object {
        const val TAG = "Http"
    }
}

internal class RequestHeadersLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        synchronized(this) {
            val context = checkNotNull(request.tag(RpcRequestContext::class.java))
            Timber.tag(TAG).d("Sending HTTP request for RPC request with $context to socket")
            Timber.tag(TAG).d("Request headers:")
            request.headers.log()
        }
        return chain.proceed(request)
    }
}

private fun Headers.log() {
    for (header in this) {
        val (name, value) = header.redactHeader()
        Timber.tag(TAG).d(" $name: $value")
    }
}

private const val TAG = "Http"
