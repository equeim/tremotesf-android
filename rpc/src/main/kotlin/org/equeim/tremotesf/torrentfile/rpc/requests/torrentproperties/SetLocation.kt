package org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.NotNormalizedRpcPath
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcResponseWithoutArguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setTorrentsLocation(hashStrings: List<String>, newDownloadDirectory: String, moveFiles: Boolean) {
    performRequest<RpcResponseWithoutArguments, _>(RpcMethod.TorrentSetLocation, SetLocationRequestArguments(hashStrings, NotNormalizedRpcPath(newDownloadDirectory), moveFiles))
}

@Serializable
private data class SetLocationRequestArguments(
    @SerialName("ids")
    val hashStrings: List<String>,
    @Contextual
    @SerialName("location")
    val newDownloadDirectory: NotNormalizedRpcPath,
    @SerialName("move")
    val moveFiles: Boolean,
)
