// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestContext
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsFinishedState(): List<TorrentFinishedState> =
    if (checkServerCapabilities(
            force = false,
            RpcRequestContext(RpcMethod.TorrentGet, "getTorrentsFinishedState")
        ).hasTableMode
    ) {
        performRequest<RpcResponse<TorrentsFinishedStateTableResponseArguments>>(
            TORRENTS_FINISHED_STATE_TABLE_REQUEST,
            "getTorrentsFinishedState"
        ).arguments.torrents
    } else {
        performRequest<RpcResponse<TorrentsFinishedStateObjectsResponseArguments>>(
            TORRENTS_FINISHED_STATE_OBJECTS_REQUEST,
            "getTorrentsFinishedState"
        ).arguments.torrents
    }

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

@OptIn(ExperimentalSerializationApi::class)
private val FIELDS = TorrentFinishedState.serializer().descriptor.elementNames.toList()

@Serializable
private data class TorrentsFinishedStateObjectsRequestArguments(
    @SerialName("fields")
    val fields: List<String> = FIELDS,
)

private val TORRENTS_FINISHED_STATE_OBJECTS_REQUEST =
    RpcRequestBody(RpcMethod.TorrentGet, TorrentsFinishedStateObjectsRequestArguments())

@Serializable
private data class TorrentsFinishedStateObjectsResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentFinishedState>,
)

@Serializable
private data class TorrentsFinishedStateTableRequestArguments(
    @SerialName("fields")
    val fields: List<String> = FIELDS,
    @SerialName("format")
    val format: String = "table",
)

private val TORRENTS_FINISHED_STATE_TABLE_REQUEST =
    RpcRequestBody(RpcMethod.TorrentGet, TorrentsFinishedStateTableRequestArguments())

@Serializable
private data class TorrentsFinishedStateTableResponseArguments(
    @Serializable(TorrentsFinishedStateTableSerializer::class)
    @SerialName("torrents")
    val torrents: List<TorrentFinishedState>,
)

private object TorrentsFinishedStateTableSerializer : TorrentsTableSerializer<TorrentFinishedState>(serializer())
