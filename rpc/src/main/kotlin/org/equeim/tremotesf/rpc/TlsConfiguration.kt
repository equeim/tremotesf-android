// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
// SPDX-FileCopyrightText: 2019 Thunderberry
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.util.Base64
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class TlsConfiguration(
    val sslSocketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val hostnameVerifier: HostnameVerifier?,
    val clientCertificates: List<Certificate>,
)

/**
 * @throws RuntimeException
 */
internal fun createTlsConfiguration(
    clientCertificatesString: String?,
    selfSignedCertificatesString: String?,
    serverHostname: String,
): TlsConfiguration {
    if (clientCertificatesString == null && selfSignedCertificatesString == null) {
        throw IllegalArgumentException("Either clientCertificatesString or selfSignedCertificatesString must be provided")
    }
    return try {
        val certificateFactory = CertificateFactory.getInstance("X.509")

        val trustManager = selfSignedCertificatesString?.let {
            createTrustManagerForSelfSignedCertificates(it, certificateFactory)
        } ?: createSystemTrustManager()

        val hostnameVerifier = if (selfSignedCertificatesString != null) {
            HostnameVerifier { hostname, _ -> hostname == serverHostname }
        } else {
            null
        }

        val clientCertificates: List<Certificate>
        val keyManager: KeyManager?
        if (clientCertificatesString != null) {
            clientCertificates = try {
                certificateFactory.generateCertificates(clientCertificatesString.byteInputStream()).toList()
            } catch (e: Exception) {
                throw RuntimeException("Failed to parse client's certificate chain", e)
            }
            keyManager = createKeyManagerForClientCertificate(clientCertificates, clientCertificatesString)
        } else {
            clientCertificates = emptyList()
            keyManager = null
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManager?.let { arrayOf(it) }, arrayOf(trustManager), null)
        TlsConfiguration(
            sslSocketFactory = sslContext.socketFactory,
            trustManager = trustManager,
            hostnameVerifier = hostnameVerifier,
            clientCertificates = clientCertificates,
        )
    } catch (e: Exception) {
        throw RuntimeException("Failed to set up TLS configuration", e)
    }
}

private fun createTrustManagerForSelfSignedCertificates(
    selfSignedCertificatesString: String,
    certificateFactory: CertificateFactory,
): X509TrustManager =
    try {
        createTrustManagerForCertificateChain(selfSignedCertificatesString.byteInputStream(), certificateFactory)
    } catch (e: Exception) {
        throw RuntimeException("Failed to create TrustManager for server's self signed certificate chain", e)
    }

private fun createSystemTrustManager(): X509TrustManager = try {
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(null as KeyStore?)
        trustManagers.single() as X509TrustManager
    }
} catch (e: Exception) {
    throw RuntimeException("Failed to create system TrustManager", e)
}

private fun createTrustManagerForCertificateChain(
    certificatesStream: InputStream,
    certificateFactory: CertificateFactory,
): X509TrustManager {
    val certificates = certificatesStream.use(certificateFactory::generateCertificates)!!
    if (certificates.isEmpty()) {
        throw RuntimeException("Did not read any certificates")
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    certificates.forEachIndexed { index, cert ->
        keyStore.setCertificateEntry(index.toString(), cert)
    }
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)
    return trustManagerFactory.trustManagers.single() as X509TrustManager
}

private val PRIVATE_KEY_REGEX =
    Regex(
        """.*-----BEGIN PRIVATE KEY-----([A-Za-z0-9+/=\n]+)-----END PRIVATE KEY-----.*""",
        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
    )

private fun createKeyManagerForClientCertificate(clientCertificates: List<Certificate>, clientCertificatesString: String): KeyManager {
    val clientKey = try {
        val spec = PKCS8EncodedKeySpec(
            Base64.decode(
                checkNotNull(PRIVATE_KEY_REGEX.matchEntire(clientCertificatesString)) {
                    "Failed to find private key in string"
                }.groupValues[1].trim(),
                Base64.DEFAULT
            )
        )
        KeyFactory.getInstance("RSA").generatePrivate(spec)
    } catch (e: Exception) {
        throw RuntimeException("Failed to parse client certificate's private key")
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setKeyEntry("client", clientKey, null, clientCertificates.toTypedArray())
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, null)
    return keyManagerFactory.keyManagers.single()
}
