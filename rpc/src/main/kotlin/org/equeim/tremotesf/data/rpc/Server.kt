package org.equeim.tremotesf.data.rpc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
@Parcelize
data class Server(
    @SerialName("name")
    var name: String = "",
    @SerialName("address")
    var address: String = "",
    @SerialName("port")
    var port: Int = DEFAULT_PORT,
    @SerialName("apiPath")
    var apiPath: String = DEFAULT_API_PATH,

    @SerialName("proxyType")
    var proxyType: String = "",
    @SerialName("proxyHostname")
    var proxyHostname: String = "",
    @SerialName("proxyPort")
    var proxyPort: Int = 0,
    @SerialName("proxyUser")
    var proxyUser: String = "",
    @SerialName("proxyPassword")
    var proxyPassword: String = "",

    @SerialName("httpsEnabled")
    var httpsEnabled: Boolean = false,
    @SerialName("selfSignedCertificateEnabled")
    var selfSignedCertificateEnabled: Boolean = false,
    @SerialName("selfSignedCertificate")
    var selfSignedCertificate: String = "",
    @SerialName("clientCertificateEnabled")
    var clientCertificateEnabled: Boolean = false,
    @SerialName("clientCertificate")
    var clientCertificate: String = "",

    @SerialName("authentication")
    var authentication: Boolean = false,
    @SerialName("username")
    var username: String = "",
    @SerialName("password")
    var password: String = "",

    @SerialName("updateIntervar")
    var updateInterval: Int = DEFAULT_UPDATE_INTERVAL,
    @SerialName("timeout")
    var timeout: Int = DEFAULT_TIMEOUT,

    @SerialName("autoConnectOnWifiNetworkEnabled")
    var autoConnectOnWifiNetworkEnabled: Boolean = false,
    @SerialName("autoConnectOnWifiNetworkSSID")
    var autoConnectOnWifiNetworkSSID: String = "",

    @SerialName("lastTorrents")
    @Volatile
    var lastTorrents: LastTorrents = LastTorrents(),
    @SerialName("addTorrentDialogDirectories")
    @Volatile
    var addTorrentDialogDirectories: List<String> = emptyList()
) : Parcelable {
    override fun toString() = "Server(name=$name)"

    fun nativeProxyType(): Int {
        return when (proxyType) {
            "", "Default" -> org.equeim.libtremotesf.Server.ProxyType.Default
            "HTTP" -> org.equeim.libtremotesf.Server.ProxyType.Http
            "SOCKS5" -> org.equeim.libtremotesf.Server.ProxyType.Socks5
            else -> {
                Timber.w("Unknown proxy type $proxyType")
                org.equeim.libtremotesf.Server.ProxyType.Default
            }
        }
    }

    @Serializable
    @Parcelize
    data class Torrent(
        val id: Int,
        val hashString: String,
        val name: String,
        val finished: Boolean
    ) : Parcelable

    @Serializable
    @Parcelize
    data class LastTorrents(
        val saved: Boolean = false,
        val torrents: List<Torrent> = emptyList()
    ) : Parcelable

    companion object {
        const val MINIMUM_PORT = 0
        const val MAXIMUM_PORT = 65535
        const val DEFAULT_PORT = 9091
        val portRange get() = MINIMUM_PORT..MAXIMUM_PORT

        const val DEFAULT_API_PATH = "/transmission/rpc"

        const val MINIMUM_UPDATE_INTERVAL = 1
        const val MAXIMUM_UPDATE_INTERVAL = 3600
        const val DEFAULT_UPDATE_INTERVAL = 5
        val updateIntervalRange get() = MINIMUM_UPDATE_INTERVAL..MAXIMUM_UPDATE_INTERVAL

        const val MINIMUM_TIMEOUT = 5
        const val MAXIMUM_TIMEOUT = 60
        const val DEFAULT_TIMEOUT = 30
        val timeoutRange get() = MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT

        fun fromNativeProxyType(type: Int): String {
            return when (type) {
                org.equeim.libtremotesf.Server.ProxyType.Default -> "Default"
                org.equeim.libtremotesf.Server.ProxyType.Http -> "HTTP"
                org.equeim.libtremotesf.Server.ProxyType.Socks5 -> "SOCKS5"
                else -> "Default"
            }
        }
    }
}
