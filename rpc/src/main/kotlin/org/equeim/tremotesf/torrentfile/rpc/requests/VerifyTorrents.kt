package org.equeim.tremotesf.torrentfile.rpc.requests

import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.verifyTorrents(ids: List<Int>) {
    performRequest<RpcResponseWithoutArguments, _>(RpcMethod.TorrentVerify, RequestWithIds(ids))
}
