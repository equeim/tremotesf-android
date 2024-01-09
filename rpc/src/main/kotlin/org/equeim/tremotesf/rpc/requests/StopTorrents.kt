// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.stopTorrents(ids: List<Int>) {
    performRequest<RpcResponseWithoutArguments, _>(org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentStop, RequestWithIds(ids))
}
