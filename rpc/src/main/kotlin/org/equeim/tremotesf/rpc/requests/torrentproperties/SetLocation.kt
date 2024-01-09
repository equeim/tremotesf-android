// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.NotNormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.RpcResponseWithoutArguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setTorrentsLocation(hashStrings: List<String>, newDownloadDirectory: String, moveFiles: Boolean) {
    performRequest<RpcResponseWithoutArguments, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentSetLocation,
        SetLocationRequestArguments(hashStrings, NotNormalizedRpcPath(newDownloadDirectory), moveFiles)
    )
}

@Serializable
private data class SetLocationRequestArguments(
    @SerialName("ids")
    val hashStrings: List<String>,
    @Contextual
    @SerialName("location")
    val newDownloadDirectory: NotNormalizedRpcPath,
    @SerialName("move")
    val moveFiles: Boolean,
)
