// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.RpcResponseWithoutArguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.renameTorrentFile(torrentHashString: String, filePath: String, newName: String) {
    performRequest<RpcResponseWithoutArguments, _>(
        RpcMethod.TorrentRenamePath,
        RenameTorrentFileRequestArguments(listOf(torrentHashString), filePath, newName)
    )
}

@Serializable
private data class RenameTorrentFileRequestArguments(
    @SerialName("ids")
    val ids: List<String>,
    @SerialName("path")
    val filePath: String,
    @SerialName("name")
    val newName: String,
)
