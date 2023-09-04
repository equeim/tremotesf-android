package org.equeim.tremotesf.torrentfile.rpc.requests.serversettings

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.torrentfile.rpc.requests.NotNormalizedRpcPath
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcRequestBody
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcResponse

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getDownloadingServerSettings(): DownloadingServerSettings =
    performRequest<RpcResponse<DownloadingServerSettings>>(DOWNLOADING_SERVER_SETTINGS_REQUEST_BODY, "getDownloadingServerSettings").arguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setDownloadDirectory(value: String) =
    setSessionProperty("download-dir", NotNormalizedRpcPath(value))

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setStartAddedTorrents(value: Boolean) =
    setSessionProperty("start-added-torrents", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setRenameIncompleteFiles(value: Boolean) =
    setSessionProperty("rename-partial-files", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setIncompleteDirectoryEnabled(value: Boolean) =
    setSessionProperty("incomplete-dir-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setIncompleteDirectory(value: String) =
    setSessionProperty("incomplete-dir", NotNormalizedRpcPath(value))

@Serializable
data class DownloadingServerSettings(
    @Contextual
    @SerialName("download-dir")
    val downloadDirectory: NormalizedRpcPath,
    @SerialName("start-added-torrents")
    val startAddedTorrents: Boolean,
    @SerialName("rename-partial-files")
    val renameIncompleteFiles: Boolean,
    @SerialName("incomplete-dir-enabled")
    val incompleteDirectoryEnabled: Boolean,
    @Contextual
    @SerialName("incomplete-dir")
    val incompleteDirectory: NormalizedRpcPath
)

@Serializable
private data class DownloadingServerSettingsRequestArguments(
    @SerialName("fields")
    @OptIn(ExperimentalSerializationApi::class)
    val fields: List<String> = DownloadingServerSettings.serializer().descriptor.elementNames.toList(),
)

private val DOWNLOADING_SERVER_SETTINGS_REQUEST_BODY = RpcRequestBody(RpcMethod.SessionGet, DownloadingServerSettingsRequestArguments())
