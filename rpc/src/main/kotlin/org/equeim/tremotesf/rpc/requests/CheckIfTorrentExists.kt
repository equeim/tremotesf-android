// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @return Torrent's name, if it exists
 * @throws RpcRequestError
 */
suspend fun RpcClient.checkIfTorrentExists(hashString: String): String? =
    performRequest<RpcResponse<SingleTorrentResponseArguments<TorrentExistsFields>>, _>(
        method = RpcMethod.TorrentGet,
        arguments = SingleTorrentRequestArguments(hashString, "name"),
        callerContext = "checkIfTorrentExists"
    ).arguments.torrents.firstOrNull()?.name

@Serializable
private data class TorrentExistsFields(
    @SerialName("name")
    val name: String
)
