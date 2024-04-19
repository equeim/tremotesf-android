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
import org.equeim.tremotesf.rpc.requests.TorrentGetRequestForFields
import org.equeim.tremotesf.rpc.requests.TorrentGetResponseForFields
import org.equeim.tremotesf.rpc.requests.TransferRate

@Serializable
data class Peer(
    @SerialName("address")
    val address: String,
    @SerialName("clientName")
    val client: String,
    @SerialName("rateToClient")
    val downloadSpeed: TransferRate,
    @SerialName("rateToPeer")
    val uploadSpeed: TransferRate,
    @SerialName("progress")
    val progress: Double,
    @SerialName("flagStr")
    val flags: String,
)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentPeers(hashString: String): List<Peer>? =
    performRequest<RpcResponse<TorrentGetResponseForFields<TorrentPeers>>, _>(
        RpcMethod.TorrentGet,
        TorrentGetRequestForFields(hashString, "peers"),
        "getTorrentPeers"
    ).arguments.torrents.firstOrNull()?.peers

@Serializable
private data class TorrentPeers(
    @SerialName("peers")
    val peers: List<Peer>,
)
