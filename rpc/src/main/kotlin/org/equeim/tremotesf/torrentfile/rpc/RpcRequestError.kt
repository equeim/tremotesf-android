// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Response
import org.equeim.tremotesf.common.causes
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate

sealed class RpcRequestError private constructor(
    internal open val response: Response? = null,
    internal val responseBody: String? = null,
    internal open val requestHeaders: Headers? = null,
    message: String? = null,
    cause: Exception? = null,
) : Exception(message, cause) {
    class NoConnectionConfiguration : RpcRequestError(message = "No connection configuration")

    class BadConnectionConfiguration(override val cause: Exception) :
        RpcRequestError(
            message = "Bad connection configuration",
            cause = cause
        )

    class ConnectionDisabled : RpcRequestError(message = "Connection to server is disabled")

    class Timeout internal constructor(response: Response?, requestHeaders: Headers?) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Timed out when performing HTTP request"
        )

    class NetworkError internal constructor(
        response: Response?,
        requestHeaders: Headers?,
        override val cause: IOException,
    ) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Network error when performing HTTP request",
            cause = cause
        )

    class UnsuccessfulHttpStatusCode internal constructor(
        override val response: Response,
        responseBody: String?,
        override val requestHeaders: Headers,
    ) :
        RpcRequestError(
            response = response,
            responseBody = responseBody,
            requestHeaders = requestHeaders,
            message = response.status
        )

    class DeserializationError internal constructor(
        override val response: Response,
        override val requestHeaders: Headers,
        override val cause: SerializationException,
    ) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Failed to deserialize server response",
            cause = cause
        )

    class AuthenticationError internal constructor(
        override val response: Response,
        override val requestHeaders: Headers,
    ) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Server requires HTTP authentication"
        )

    class UnsupportedServerVersion internal constructor(
        val version: String,
        override val response: Response,
        override val requestHeaders: Headers,
    ) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Transmission version $version is not supported"
        )

    class UnsuccessfulResultField internal constructor(
        val result: String,
        override val response: Response,
        override val requestHeaders: Headers,
    ) :
        RpcRequestError(
            response = response,
            responseBody = "Response result is '$result'",
            requestHeaders = requestHeaders
        )

    class UnexpectedError internal constructor(
        override val response: Response,
        override val requestHeaders: Headers,
        override val cause: Exception,
    ) :
        RpcRequestError(
            response = response,
            requestHeaders = requestHeaders,
            message = "Unexpected error",
            cause = cause
        )
}

val RpcRequestError.isRecoverable: Boolean
    get() = when (this) {
        is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.BadConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> false
        else -> true
    }

internal fun IOException.toRpcRequestError(call: Call, response: Response?, requestHeaders: Headers?): RpcRequestError =
    if (this is SocketTimeoutException || call.isCanceled()) {
        RpcRequestError.Timeout(response = response, requestHeaders = requestHeaders)
    } else {
        RpcRequestError.NetworkError(response = response, requestHeaders = requestHeaders, cause = this)
    }

@Parcelize
data class DetailedRpcRequestError(
    val error: RpcRequestError,
    val suppressedErrors: List<Throwable>,
    val responseInfo: ResponseInfo?,
    val serverCertificates: List<Certificate>,
    val clientCertificates: List<Certificate>,
    val requestHeaders: List<Pair<String, String>>,
) : Parcelable {
    @Parcelize
    data class ResponseInfo(
        val status: String,
        val protocol: String,
        val tlsHandshakeInfo: TlsHandshakeInfo?,
        val headers: List<Pair<String, String>>,
    ) : Parcelable

    @Parcelize
    data class TlsHandshakeInfo(val tlsVersion: String, val cipherSuite: String) : Parcelable
}

fun RpcRequestError.makeDetailedError(client: RpcClient): DetailedRpcRequestError {
    return DetailedRpcRequestError(
        error = this,
        suppressedErrors = causes.flatMap { it.suppressed.asSequence() }.toList(),
        responseInfo = response?.run {
            DetailedRpcRequestError.ResponseInfo(
                status = status,
                protocol = protocol.toString(),
                tlsHandshakeInfo = handshake?.run {
                    DetailedRpcRequestError.TlsHandshakeInfo(
                        tlsVersion = tlsVersion.javaName,
                        cipherSuite = cipherSuite.javaName,
                    )
                },
                headers = headers.toList(),
            )
        },
        serverCertificates = response?.run {
            withPriorResponses.flatMap { it.handshake?.peerCertificates.orEmpty() }.toSet().toList()
        }
            ?: causes.filterIsInstance<CertPathValidatorException>().firstOrNull()?.certPath?.certificates.orEmpty(),
        clientCertificates = response?.run {
            withPriorResponses.flatMap { it.handshake?.localCertificates.orEmpty() }.toSet().toList()
        }
            ?: client.getConnectionConfiguration().value?.getOrNull()?.clientCertificates.orEmpty(),
        requestHeaders = requestHeaders?.toList().orEmpty(),
    )
}

private val Response.withPriorResponses: Sequence<Response> get() = generateSequence(this) { it.priorResponse }
