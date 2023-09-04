package org.equeim.tremotesf.torrentfile.rpc

internal const val SESSION_ID_HEADER = "X-Transmission-Session-Id"
internal const val AUTHORIZATION_HEADER = "Authorization"
internal const val PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization"

internal fun Pair<String, String>.redactHeader(): Pair<String, String> = when (first) {
    AUTHORIZATION_HEADER, PROXY_AUTHORIZATION_HEADER -> first to REDACTED_VALUE
    else -> this
}

private const val REDACTED_VALUE = "***"
