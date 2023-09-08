// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertPathValidatorException

sealed class RpcRequestError private constructor(
    internal open val response: Response?,
    message: String? = null,
    cause: Exception? = null,
) : Exception(message, cause) {
    class NoConnectionConfiguration : RpcRequestError(null, "No connection configuration")

    class BadConnectionConfiguration(override val cause: Exception) :
        RpcRequestError(null, "Bad connection configuration", cause)

    class ConnectionDisabled : RpcRequestError(null, "Connection to server is disabled")

    class Timeout internal constructor(context: String, response: Response?) :
        RpcRequestError(response, "Timed out when $context")

    class NetworkError internal constructor(override val cause: IOException, context: String, response: Response?) :
        RpcRequestError(response, "Network error when $context", cause)

    class UnsuccessfulHttpStatusCode internal constructor(override val response: Response) :
        RpcRequestError(response, response.status)

    class DeserializationError(override val cause: SerializationException, override val response: Response) :
        RpcRequestError(response, "Failed to deserialize server response", cause)

    class AuthenticationError internal constructor(override val response: Response) :
        RpcRequestError(response, "Server requires HTTP authentication")

    class UnsupportedServerVersion internal constructor(val version: String, override val response: Response) :
        RpcRequestError(response, "Transmission version $version is not supported")

    class UnsuccessfulResultField internal constructor(val result: String, override val response: Response) :
        RpcRequestError(response, "Response result is '$result'")

    class UnknownError internal constructor(override val cause: Exception, override val response: Response) :
        RpcRequestError(response, "Unexpected error", cause)
}

val RpcRequestError.isRecoverable: Boolean
    get() = when (this) {
        is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.BadConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> false
        else -> true
    }

internal fun IOException.toRpcRequestError(call: Call, context: String, response: Response?): RpcRequestError =
    if (this is SocketTimeoutException || call.isCanceled()) {
        RpcRequestError.Timeout(context, response)
    } else {
        RpcRequestError.NetworkError(this, context, response)
    }

@Parcelize
data class DetailedRpcRequestErrorString(
    val detailedError: String,
    val certificates: String?,
) : Parcelable

fun RpcRequestError.makeDetailedErrorString(): DetailedRpcRequestErrorString {
    val suppressed = causes.flatMap { it.suppressed.asSequence() }.toList()
    return DetailedRpcRequestErrorString(
        detailedError = buildString {
            append("Error:\n")
            appendThrowable(this@makeDetailedErrorString)
            if (suppressed.isNotEmpty()) {
                append("Suppressed exceptions:\n")
                suppressed.forEach(::appendThrowable)
            }
            response?.withPriorResponses?.forEach { response ->
                append("HTTP request:\n")
                append("- URL: ${response.request.url}\n")
                append("HTTP Response:\n")
                append("- Protocol: ${response.protocol}\n")
                append("- Status: ${response.status}\n")
                append("- Headers:\n")
                response.headers.forEach { header ->
                    val (name, value) = header.redactHeader()
                    append("   $name: $value\n")
                }
                response.handshake?.let { handshake ->
                    append("- TLS info:\n")
                    append("  - Version: ${handshake.tlsVersion.javaName}\n")
                    append("  - Cipher suite: ${handshake.cipherSuite.javaName}\n")
                }
            }
        },
        certificates = run {
            val clientCerts =
                response?.withPriorResponses?.flatMap { it.handshake?.localCertificates.orEmpty() }?.toSet().orEmpty()
            val serverCerts =
                response?.withPriorResponses?.flatMap { it.handshake?.peerCertificates.orEmpty() }?.toSet()
                    ?: causes.filterIsInstance<CertPathValidatorException>()
                        .firstOrNull()?.certPath?.certificates.orEmpty()
            if (clientCerts.isNotEmpty() || serverCerts.isNotEmpty()) {
                buildString {
                    if (clientCerts.isNotEmpty()) {
                        append("Client certificates:\n")
                        clientCerts.forEach { append(" $it\n") }
                    }
                    if (serverCerts.isNotEmpty()) {
                        append("Server certificates:\n")
                        serverCerts.forEach { append(" $it\n") }
                    }
                }
            } else {
                null
            }
        }
    )
}

private fun StringBuilder.appendThrowable(e: Throwable) {
    append("$e\n\n")
    for (cause in e.causes) {
        append("Caused by:\n$cause\n\n")
    }
}

private val Throwable.causes: Sequence<Throwable> get() = generateSequence(cause, Throwable::cause)
private val Response.withPriorResponses: Sequence<Response> get() = generateSequence(this) { it.priorResponse }
