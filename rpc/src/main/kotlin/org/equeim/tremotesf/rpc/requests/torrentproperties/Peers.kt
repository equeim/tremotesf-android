// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcResponse
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
    performRequest<RpcResponse<TorrentPeersResponseArguments>, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentGet,
        TorrentPeersRequestArguments(hashString),
        "getTorrentPeers"
    ).arguments.torrents.firstOrNull()?.peers

@Serializable
private data class TorrentPeersRequestArguments(
    @SerialName("ids")
    val ids: List<String>,

    @SerialName("fields")
    val fields: List<String> = listOf("peers"),
) {
    constructor(hashString: String) : this(ids = listOf(hashString))
}

@Serializable
private data class TorrentPeersResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentFields>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }

    @Serializable
    data class TorrentFields(
        @SerialName("peers")
        val peers: List<Peer>,
    )
}
