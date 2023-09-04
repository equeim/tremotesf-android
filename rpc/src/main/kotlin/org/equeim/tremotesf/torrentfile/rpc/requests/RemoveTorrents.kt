package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.removeTorrents(hashStrings: List<String>, deleteFiles: Boolean) {
    performRequest<RpcResponseWithoutArguments, _>(RpcMethod.TorrentRemove, RemoveTorrentsRequestArguments(hashStrings, deleteFiles))
}

@Serializable
private data class RemoveTorrentsRequestArguments(
    @SerialName("ids")
    val hashStrings: List<String>,
    @SerialName("delete-local-data")
    val deleteFiles: Boolean,
)