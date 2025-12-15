// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @return Torrent's name, if it exists
 * @throws RpcRequestError
 */
suspend fun RpcClient.checkIfTorrentsExist(hashStrings: List<String>): List<ExistingTorrent> =
    performRequest<TorrentsExistenceResponseArguments, _>(
        method = RpcMethod.TorrentGet,
        arguments = TorrentsExistenceRequestArguments(
            hashStrings = hashStrings,
            fields = ExistingTorrent.serializer().descriptor.elementNames.toList()
        ),
        callerContext = "checkIfTorrentExists"
    ).arguments.torrents

@Serializable
private data class TorrentsExistenceRequestArguments(
    @SerialName("ids")
    val hashStrings: List<String>,
    @SerialName("fields")
    val fields: List<String>,
)

@Serializable
private data class TorrentsExistenceResponseArguments(
    @SerialName("torrents")
    val torrents: List<ExistingTorrent>
)

@Serializable
data class ExistingTorrent(
    @SerialName("hashString")
    val hashString: String,
    @SerialName("name")
    val name: String
)
