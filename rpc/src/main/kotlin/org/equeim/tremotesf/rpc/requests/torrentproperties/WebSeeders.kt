// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.SingleTorrentRequestArguments
import org.equeim.tremotesf.rpc.requests.SingleTorrentResponseArguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentWebSeeders(hashString: String): List<String>? =
    performRequest<SingleTorrentResponseArguments<TorrentWebSeeders>, _>(
        RpcMethod.TorrentGet,
        SingleTorrentRequestArguments(hashString, "webseeds"),
        "getTorrentWebSeeders"
    ).arguments.torrents.firstOrNull()?.webSeeders

@Serializable
private data class TorrentWebSeeders(@SerialName("webseeds") val webSeeders: List<String>)
