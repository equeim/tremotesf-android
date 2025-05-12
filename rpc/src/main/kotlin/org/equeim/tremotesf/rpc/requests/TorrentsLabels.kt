package org.equeim.tremotesf.rpc.requests

// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsLabels(): Set<String> =
    performAllTorrentsRequest<TorrentsLabelsFields>(
        objectsFormatRequestBody = TORRENTS_LABELS_OBJECTS_REQUEST,
        tableFormatRequestBody = TORRENTS_LABELS_TABLE_REQUEST,
        callerContext = "getTorrentsLabels"
    ).flatMapTo(mutableSetOf(), TorrentsLabelsFields::labels)

private val FIELDS = listOf("labels")
private val TORRENTS_LABELS_OBJECTS_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = false))
private val TORRENTS_LABELS_TABLE_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = true))
@Serializable
private data class TorrentsLabelsFields(
    @SerialName("labels")
    val labels: List<String>,
)
