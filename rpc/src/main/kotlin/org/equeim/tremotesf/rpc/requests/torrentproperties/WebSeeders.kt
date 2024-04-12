// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.RpcResponse

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentWebSeeders(hashString: String): List<String>? =
    performRequest<RpcResponse<TorrentGetResponseForFields<TorrentWebSeeders>>, _>(
        RpcMethod.TorrentGet,
        TorrentGetRequestForFields(hashString, "webseeds"),
        "getTorrentWebSeeders"
    ).arguments.torrents.firstOrNull()?.webSeeders

@Serializable
private data class TorrentWebSeeders(@SerialName("webseeds") val webSeeders: List<String>)
