@file:UseSerializers(DurationSerializer::class, ProxyTypeSerializer::class)

package org.equeim.tremotesf.torrentfile.rpc

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.libtremotesf.ConnectionConfiguration
import org.equeim.libtremotesf.ConnectionConfiguration.ProxyType
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
@Parcelize
@TypeParceler<Duration, DurationParceler>
data class Server(
    @SerialName("name")
    val name: String = "",
    @SerialName("address")
    val address: String = "",
    @SerialName("port")
    val port: Int = DEFAULT_PORT,
    @SerialName("apiPath")
    val apiPath: String = DEFAULT_API_PATH,

    @SerialName("proxyType")
    val proxyType: ProxyType = ProxyType.Default,
    @SerialName("proxyHostname")
    val proxyHostname: String = "",
    @SerialName("proxyPort")
    val proxyPort: Int = 0,
    @SerialName("proxyUser")
    val proxyUser: String = "",
    @SerialName("proxyPassword")
    val proxyPassword: String = "",

    @SerialName("httpsEnabled")
    val httpsEnabled: Boolean = false,
    @SerialName("selfSignedCertificateEnabled")
    val selfSignedCertificateEnabled: Boolean = false,
    @SerialName("selfSignedCertificate")
    val selfSignedCertificate: String = "",
    @SerialName("clientCertificateEnabled")
    val clientCertificateEnabled: Boolean = false,
    @SerialName("clientCertificate")
    val clientCertificate: String = "",

    @SerialName("authentication")
    val authentication: Boolean = false,
    @SerialName("username")
    val username: String = "",
    @SerialName("password")
    val password: String = "",

    @SerialName("updateInterval")
    val updateInterval: Duration = DEFAULT_UPDATE_INTERVAL,
    @SerialName("timeout")
    val timeout: Duration = DEFAULT_TIMEOUT,

    @SerialName("autoConnectOnWifiNetworkEnabled")
    val autoConnectOnWifiNetworkEnabled: Boolean = false,
    @SerialName("autoConnectOnWifiNetworkSSID")
    val autoConnectOnWifiNetworkSSID: String = "",

    @SerialName("lastTorrents")
    val lastTorrents: LastTorrents = LastTorrents(),
    @SerialName("addTorrentDialogDirectories")
    val lastDownloadDirectories: List<String> = emptyList(),
    @SerialName("lastDownloadDirectory")
    val lastDownloadDirectory: String? = null
) : Parcelable {
    override fun toString() = "Server(name=$name)"

    @Serializable
    @Parcelize
    data class Torrent(
        @SerialName("id")
        val id: Int,
        @SerialName("hashString")
        val hashString: String,
        @SerialName("name")
        val name: String,
        @SerialName("finished")
        val finished: Boolean
    ) : Parcelable

    @Serializable
    @Parcelize
    data class LastTorrents(
        @SerialName("saved")
        val saved: Boolean = false,
        @SerialName("torrents")
        val torrents: List<Torrent> = emptyList()
    ) : Parcelable

    companion object {
        private const val MINIMUM_PORT = 0
        private const val MAXIMUM_PORT = 65535
        val portRange get() = MINIMUM_PORT..MAXIMUM_PORT
        const val DEFAULT_PORT = 9091

        const val DEFAULT_API_PATH = "/transmission/rpc"

        val MINIMUM_UPDATE_INTERVAL = 1.seconds
        val MAXIMUM_UPDATE_INTERVAL = 1.hours
        val updateIntervalRange get() = MINIMUM_UPDATE_INTERVAL..MAXIMUM_UPDATE_INTERVAL
        val DEFAULT_UPDATE_INTERVAL = 5.seconds

        val MINIMUM_TIMEOUT = 5.seconds
        val MAXIMUM_TIMEOUT = 60.seconds
        val timeoutRange get() = MINIMUM_TIMEOUT..MAXIMUM_TIMEOUT
        val DEFAULT_TIMEOUT = 30.seconds
    }
}

private object DurationSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor(DurationSerializer::class.qualifiedName!!, PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Duration = decoder.decodeInt().seconds
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeInt(value.inWholeSeconds.toInt())
}

private object DurationParceler : Parceler<Duration> {
    override fun create(parcel: Parcel): Duration = parcel.readLong().milliseconds
    override fun Duration.write(parcel: Parcel, flags: Int) = parcel.writeLong(inWholeMilliseconds)
}

private object ProxyTypeSerializer : KSerializer<ProxyType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(ProxyTypeSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    private val strings = arrayOf(
        ProxyType.Default to "Default",
        ProxyType.Http to "HTTP",
        ProxyType.Socks5 to "SOCKS5"
    )

    override fun deserialize(decoder: Decoder): ProxyType {
        val typeString = decoder.decodeString()
        if (typeString.isEmpty()) return ProxyType.Default
        return strings.find { it.second == typeString }?.first ?: run {
            Timber.e("Unknown proxy type $typeString")
            ProxyType.Default
        }
    }

    override fun serialize(encoder: Encoder, value: ProxyType) =
        encoder.encodeString(requireNotNull(strings.find { it.first == value }?.second))
}

fun Server.toConnectionConfiguration() = ConnectionConfiguration().also { s ->
    s.address = address
    s.port = port
    s.apiPath = apiPath

    s.proxyType = proxyType
    s.proxyHostname = proxyHostname
    s.proxyPort = proxyPort
    s.proxyUser = proxyUser
    s.proxyPassword = proxyPassword

    s.https = httpsEnabled
    s.selfSignedCertificateEnabled = selfSignedCertificateEnabled
    s.selfSignedCertificate = selfSignedCertificate.toByteArray()
    s.clientCertificateEnabled = clientCertificateEnabled
    s.clientCertificate = clientCertificate.toByteArray()

    s.authentication = authentication
    s.username = username
    s.password = password

    s.updateInterval = updateInterval.inWholeSeconds.toInt()
    s.timeout = timeout.inWholeSeconds.toInt()
}
