package org.equeim.tremotesf.torrentfile.rpc.requests

import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.reannounceTorrents(ids: List<Int>) {
    performRequest<RpcResponseWithoutArguments, _>(RpcMethod.TorrentReannounce, RequestWithIds(ids))
}
