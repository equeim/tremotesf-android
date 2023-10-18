// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

internal class RealRequestHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = checkNotNull(request.tag(RpcRequestContext::class.java))
        realRequestHeaders[context] = request.headers
        return chain.proceed(request)
    }
}

internal fun consumeRealRequestHeaders(context: RpcRequestContext): Headers? = realRequestHeaders.remove(context)

private val realRequestHeaders = ConcurrentHashMap<RpcRequestContext, Headers>()
