package org.equeim.tremotesf.rpc.requests

// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestContext
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsLabels(): Set<String> =
    if (checkServerCapabilities(
            force = false,
            RpcRequestContext(RpcMethod.TorrentGet, "getTorrentsLabels")
        ).hasTableMode
    ) {
        performRequest<RpcResponse<TorrentsLabelsTableResponseArguments>>(
            TORRENTS_LABELS_TABLE_REQUEST,
            "getTorrentsLabels"
        ).arguments.torrents
    } else {
        performRequest<RpcResponse<TorrentsLabelsObjectsResponseArguments>>(
            TORRENTS_LABELS_OBJECTS_REQUEST,
            "getTorrentsLabels"
        ).arguments.torrents
    }.flatMapTo(mutableSetOf(), TorrentsLabelsFields::labels)

@Serializable
private data class TorrentsLabelsObjectsRequestArguments(
    @SerialName("fields")
    val fields: List<String> = listOf("labels"),
)

private val TORRENTS_LABELS_OBJECTS_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, TorrentsLabelsObjectsRequestArguments())

@Serializable
private data class TorrentsLabelsObjectsResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentsLabelsFields>,
)

@Serializable
private data class TorrentsLabelsTableRequestArguments(
    @SerialName("fields")
    val fields: List<String> = listOf("labels"),
    @SerialName("format")
    val format: String = "table",
)

private val TORRENTS_LABELS_TABLE_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, TorrentsLabelsTableRequestArguments())

@Serializable
private data class TorrentsLabelsTableResponseArguments(
    @Serializable(TorrentsLabelsTableSerializer::class)
    @SerialName("torrents")
    val torrents: List<TorrentsLabelsFields>,
)

@Serializable
private data class TorrentsLabelsFields(
    @SerialName("labels")
    val labels: List<String>,
)

private object TorrentsLabelsTableSerializer :
    TorrentsTableSerializer<TorrentsLabelsFields>(serializer())
