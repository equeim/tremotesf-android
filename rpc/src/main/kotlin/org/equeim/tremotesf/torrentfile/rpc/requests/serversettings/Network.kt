package org.equeim.tremotesf.torrentfile.rpc.requests.serversettings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcRequestBody
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcResponse

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getNetworkServerSettings(): NetworkServerSettings =
    performRequest<RpcResponse<NetworkServerSettings>>(NETWORK_SERVER_SETTINGS_REQUEST_BODY, "getNetworkServerSettings").arguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setPeerPort(value: Int) =
    setSessionProperty("peer-port", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUseRandomPort(value: Boolean) =
    setSessionProperty("peer-port-random-on-start", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUsePortForwarding(value: Boolean) =
    setSessionProperty("port-forwarding-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setEncryptionMode(value: NetworkServerSettings.EncryptionMode) =
    setSessionProperty("encryption", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUseUTP(value: Boolean) =
    setSessionProperty("utp-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUsePEX(value: Boolean) =
    setSessionProperty("pex-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUseDHT(value: Boolean) =
    setSessionProperty("dht-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUseLPD(value: Boolean) =
    setSessionProperty("lpd-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setMaximumPeersPerTorrent(value: Int) =
    setSessionProperty("peer-limit-per-torrent", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setMaximumPeersGlobally(value: Int) =
    setSessionProperty("peer-limit-global", value)

@Serializable
data class NetworkServerSettings(
    @SerialName("peer-port")
    val peerPort: Int,
    @SerialName("peer-port-random-on-start")
    val useRandomPort: Boolean,
    @SerialName("port-forwarding-enabled")
    val usePortForwarding: Boolean,
    @SerialName("encryption")
    val encryptionMode: EncryptionMode,
    @SerialName("utp-enabled")
    val useUTP: Boolean,
    @SerialName("pex-enabled")
    val usePEX: Boolean,
    @SerialName("dht-enabled")
    val useDHT: Boolean,
    @SerialName("lpd-enabled")
    val useLPD: Boolean,
    @SerialName("peer-limit-per-torrent")
    val maximumPeersPerTorrent: Int,
    @SerialName("peer-limit-global")
    val maximumPeersGlobally: Int,
) {
    @Serializable
    enum class EncryptionMode {
        @SerialName("tolerated")
        Allowed,
        @SerialName("preferred")
        Preferred,
        @SerialName("required")
        Required
    }
}

@Serializable
private data class NetworkServerSettingsRequestArguments(
    @SerialName("fields")
    @OptIn(ExperimentalSerializationApi::class)
    val fields: List<String> = NetworkServerSettings.serializer().descriptor.elementNames.toList(),
)

private val NETWORK_SERVER_SETTINGS_REQUEST_BODY = RpcRequestBody(RpcMethod.SessionGet, NetworkServerSettingsRequestArguments())
