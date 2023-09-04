// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

internal data class ConnectionConfiguration(
    val httpClient: OkHttpClient,
    val url: HttpUrl,
    val credentials: String?,
    val updateInterval: Duration,
)

/**
 * @throws RuntimeException
 */
internal fun createConnectionConfiguration(server: Server): ConnectionConfiguration {
    val url = createUrl(server)
    val builder = OkHttpClient.Builder()
        .addInterceptor(LoggingInterceptor())
        .addNetworkInterceptor(RequestHeadersLoggingInterceptor())
        .callTimeout(server.timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .proxy(server.proxyType?.let { Proxy(it, InetSocketAddress.createUnresolved(server.proxyHostname, server.proxyPort)) })
    if (server.clientCertificateEnabled || server.selfSignedCertificateEnabled) {
        val (sslSocketFactory, trustManager) = createSslSocketFactory(
            if (server.clientCertificateEnabled) server.clientCertificate else null,
            if (server.selfSignedCertificateEnabled) server.selfSignedCertificate else null
        )
        builder.sslSocketFactory(sslSocketFactory, trustManager)
        if (server.selfSignedCertificateEnabled) {
            builder.hostnameVerifier { hostname, _ -> hostname == url.host }
        }
    }
    return ConnectionConfiguration(
        httpClient = builder.build(),
        url = url,
        credentials = if (server.authentication) Credentials.basic(server.username, server.password) else null,
        updateInterval = server.updateInterval
    )
}

/**
 * @throws RuntimeException
 */
private fun createUrl(server: Server): HttpUrl = try {
    HttpUrl.Builder()
        .scheme(if (server.httpsEnabled) "https" else "http")
        .host(server.address.also {
            if (it.isBlank()) throw RuntimeException("Server's address can't be empty")
        })
        .port(server.port)
        .apply {
            val apiPath = server.apiPath.removePrefix("/")
            val queryStart = apiPath.indexOf('?')
            if (queryStart == -1) {
                addPathSegments(apiPath)
            } else {
                addPathSegments(apiPath.substring(0, queryStart))
                if (queryStart != apiPath.lastIndex) {
                    query(apiPath.substring(queryStart + 1))
                }
            }
        }
        .build()
} catch (e: Exception) {
    throw RuntimeException("Failed to create url", e)
}

fun Server.shouldUpdateConnectionConfiguration(newServer: Server): Boolean =
    address != newServer.address ||
            port != newServer.port ||
            apiPath != newServer.apiPath ||
            proxyType != newServer.proxyType ||
            proxyHostname != newServer.proxyHostname ||
            proxyPort != newServer.proxyPort ||
            proxyUser != newServer.proxyUser ||
            proxyPassword != newServer.proxyPassword ||
            httpsEnabled != newServer.httpsEnabled ||
            selfSignedCertificateEnabled != newServer.selfSignedCertificateEnabled ||
            selfSignedCertificate != newServer.selfSignedCertificate ||
            clientCertificateEnabled != newServer.clientCertificateEnabled ||
            clientCertificate != newServer.clientCertificate ||
            authentication != newServer.authentication ||
            username != newServer.username ||
            password != newServer.password ||
            updateInterval != newServer.updateInterval ||
            timeout != newServer.timeout
