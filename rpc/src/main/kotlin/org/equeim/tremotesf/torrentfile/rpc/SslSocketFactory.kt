// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * @throws RuntimeException
 */
internal fun createSslSocketFactory(
    clientCertificate: String?,
    selfSignedCertificate: String?,
): Pair<SSLSocketFactory, X509TrustManager> = try {
    val trustManager = createTrustManager(selfSignedCertificate)
    val keyManager = clientCertificate?.let(::createKeyManager)
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManager?.let { arrayOf(it) }, arrayOf(trustManager), SecureRandom())
    sslContext.socketFactory to trustManager
} catch (e: Exception) {
    throw RuntimeException("Failed to set up TLS configuration", e)
}

private fun createTrustManager(selfSignedCertificate: String?): X509TrustManager {
    val keyStore = selfSignedCertificate?.let {
        val selfSignedChain = CertificateFactory.getInstance("X.509").generateCertificates(it.byteInputStream())
            .filterIsInstance<X509Certificate>()
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        selfSignedChain.forEachIndexed { index, cert ->
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

private fun createKeyManager(clientCertificate: String): KeyManager {
    val clientCertChain = CertificateFactory.getInstance("X.509")
        .generateCertificates(clientCertificate.byteInputStream()).toTypedArray()
    val clientKeySpec = PKCS8EncodedKeySpec(
        Base64.decode(
            checkNotNull(PRIVATE_KEY_REGEX.matchEntire(clientCertificate)) {
                "Failed to find private key in string"
            }.groupValues[1].trim(),
            Base64.DEFAULT
        )
    )
    val clientKey = KeyFactory.getInstance("RSA").generatePrivate(clientKeySpec)
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setKeyEntry("client", clientKey, null, clientCertChain)
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, null)
    return keyManagerFactory.keyManagers.single()
}
