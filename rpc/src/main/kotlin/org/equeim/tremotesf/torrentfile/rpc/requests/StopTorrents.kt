// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.stopTorrents(ids: List<Int>) {
    performRequest<RpcResponseWithoutArguments, _>(RpcMethod.TorrentStop, RequestWithIds(ids))
}
