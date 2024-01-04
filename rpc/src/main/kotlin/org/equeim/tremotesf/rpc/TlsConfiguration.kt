// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
// SPDX-FileCopyrightText: 2019 Thunderberry
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.X509TrustManagerExtensions
import android.os.Build
import android.util.Base64
import androidx.annotation.Keep
import java.io.InputStream
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
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
    context: Context,
): TlsConfiguration? {
    // We need to set up ISRG Root X1 certificate for Android < 7.1
    if (clientCertificatesString == null && selfSignedCertificatesString == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        return null
    }
    return try {
        val certificateFactory = CertificateFactory.getInstance("X.509")

        val trustManager = selfSignedCertificatesString?.let {
            createTrustManagerForSelfSignedCertificates(it, certificateFactory)
        } ?: createDefaultTrustManager(certificateFactory, context)

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

private fun createDefaultTrustManager(
    certificateFactory: CertificateFactory,
    context: Context,
): X509TrustManager = try {
    val defaultTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(null as KeyStore?)
        trustManagers.single() as X509TrustManager
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        defaultTrustManager
    } else {
        CompositeX509TrustManager(
            defaultTrustManager,
            createTrustManagerForCertificateChain(
                context.resources.openRawResource(R.raw.isrgrootx1),
                certificateFactory
            )
        )
    }
} catch (e: Exception) {
    throw RuntimeException("Failed to create default TrustManager", e)
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

@SuppressLint("CustomX509TrustManager")
private class CompositeX509TrustManager(vararg trustManagers: X509TrustManager) : X509TrustManager {
    private class TrustManagerAndExtensions(
        val trustManager: X509TrustManager,
        val extensions: X509TrustManagerExtensions,
    ) {
        constructor(trustManager: X509TrustManager) : this(trustManager, X509TrustManagerExtensions(trustManager))
    }

    private val trustManagers: List<TrustManagerAndExtensions> = trustManagers.map(CompositeX509TrustManager::TrustManagerAndExtensions)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted { trustManager.checkClientTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        checkTrusted { trustManager.checkServerTrusted(chain, authType) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        trustManagers.asSequence().flatMap { it.trustManager.acceptedIssuers.asSequence() }.toList().toTypedArray()

    @Suppress("unused")
    @Keep
    fun checkServerTrusted(
        chain: Array<X509Certificate?>?, authType: String?, host: String?,
    ): List<X509Certificate> {
        return checkTrusted { extensions.checkServerTrusted(chain, authType, host) }
    }

    private fun <T> checkTrusted(check: TrustManagerAndExtensions.() -> T): T {
        val certificateExceptions = mutableListOf<CertificateException>()
        for (trustManager in trustManagers) {
            try {
                return trustManager.check()
            } catch (e: CertificateException) {
                certificateExceptions.add(e)
            } catch (e: RuntimeException) {
                val cause = e.cause
                if (cause is InvalidAlgorithmParameterException) {
                    // Handling of [InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty]
                    //
                    // This is most likely a result of using a TrustManager created from an empty KeyStore.
                    // The exception will be thrown during the SSL Handshake. It is safe to suppress
                    // and can be bundle with the other exceptions to proceed validating the counterparty with
                    // the remaining TrustManagers.
                    certificateExceptions.add(CertificateException(cause))
                } else {
                    throw e
                }
            }
        }
        val certificateException = CertificateException("None of the TrustManagers trust this certificate chain")
        certificateExceptions.forEach(certificateException::addSuppressed)
        throw certificateException
    }
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
