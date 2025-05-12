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
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsFinishedState(): List<TorrentFinishedState> =
    performAllTorrentsRequest(
        objectsFormatRequestBody = TORRENTS_FINISHED_STATE_OBJECTS_REQUEST,
        tableFormatRequestBody = TORRENTS_FINISHED_STATE_TABLE_REQUEST,
        callerContext = "getTorrentsFinishedState"
    )

interface RpcTorrentFinishedState {
    val hashString: String
    val isFinished: Boolean
    val sizeWhenDone: FileSize
    val name: String
}

@Serializable
data class TorrentFinishedState(
    @SerialName("hashString")
    override val hashString: String,
    @SerialName("leftUntilDone")
    val leftUntilDone: FileSize,
    @SerialName("sizeWhenDone")
    override val sizeWhenDone: FileSize,
    @SerialName("name")
    override val name: String,
) : RpcTorrentFinishedState {
    override val isFinished: Boolean get() = leftUntilDone.bytes == 0L
}

private val FIELDS = TorrentFinishedState.serializer().descriptor.elementNames.toList()
private val TORRENTS_FINISHED_STATE_OBJECTS_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = false))
private val TORRENTS_FINISHED_STATE_TABLE_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = true))
