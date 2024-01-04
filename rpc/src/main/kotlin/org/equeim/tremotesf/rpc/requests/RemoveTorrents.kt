// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.removeTorrents(hashStrings: List<String>, deleteFiles: Boolean) {
    performRequest<RpcResponseWithoutArguments, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentRemove,
        RemoveTorrentsRequestArguments(hashStrings, deleteFiles)
    )
}

@Serializable
private data class RemoveTorrentsRequestArguments(
    @SerialName("ids")
    val hashStrings: List<String>,
    @SerialName("delete-local-data")
    val deleteFiles: Boolean,
)