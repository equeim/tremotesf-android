// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TlsConfiguration(
    val sslSocketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val clientCertificates: List<Certificate>,
)

/**
 * @throws RuntimeException
 */
internal fun createTlsConfiguration(
    clientCertificatesString: String?,
    selfSignedCertificatesString: String?,
): TlsConfiguration = try {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val trustManager = createTrustManager(selfSignedCertificatesString, certificateFactory)
    val clientCertificates: List<Certificate>
    val keyManager: KeyManager?
    if (clientCertificatesString != null) {
        clientCertificates = parseCertificates(certificateFactory, clientCertificatesString, "client")
        keyManager = createKeyManager(clientCertificates, clientCertificatesString)
    } else {
        clientCertificates = emptyList()
        keyManager = null
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManager?.let { arrayOf(it) }, arrayOf(trustManager), SecureRandom())
    TlsConfiguration(
        sslSocketFactory = sslContext.socketFactory,
        trustManager = trustManager,
        clientCertificates = clientCertificates,
    )
} catch (e: Exception) {
    throw RuntimeException("Failed to set up TLS configuration", e)
}

private fun parseCertificates(factory: CertificateFactory, certificatesString: String, who: String): List<Certificate> {
    return try {
        factory.generateCertificates(certificatesString.byteInputStream()).toList()
    } catch (e: Exception) {
        throw RuntimeException("Failed to parse $who's certificates")
    }
}

private fun createTrustManager(selfSignedCertificatesString: String?, certificateFactory: CertificateFactory): X509TrustManager {
    val keyStore = selfSignedCertificatesString?.let {
        val selfSignedCertificates = parseCertificates(certificateFactory, it, "server")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        selfSignedCertificates.forEachIndexed { index, cert ->
            keyStore.setCertificateEntry(index.toString(), cert)
        }
        keyStore
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

private fun createKeyManager(clientCertificates: List<Certificate>, clientCertificatesString: String): KeyManager {
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
