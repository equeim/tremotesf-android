// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcResponseWithoutArguments

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
