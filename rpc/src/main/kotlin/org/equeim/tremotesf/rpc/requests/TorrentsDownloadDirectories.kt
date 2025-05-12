// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsDownloadDirectories(): List<NormalizedRpcPath> =
    performAllTorrentsRequest<TorrentsDownloadDirectoriesFields>(
        objectsFormatRequestBody = TORRENTS_DOWNLOAD_DIRECTORIES_OBJECTS_REQUEST,
        tableFormatRequestBody = TORRENTS_DOWNLOAD_DIRECTORIES_TABLE_REQUEST,
        callerContext = "getTorrentsDownloadDirectories"
    ).map { it.downloadDirectory }

private val FIELDS = listOf("downloadDir")
private val TORRENTS_DOWNLOAD_DIRECTORIES_OBJECTS_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = false))
private val TORRENTS_DOWNLOAD_DIRECTORIES_TABLE_REQUEST =
    createStaticRpcRequestBody(RpcMethod.TorrentGet, AllTorrentsRequestArguments(FIELDS, table = true))

@Serializable
private data class TorrentsDownloadDirectoriesFields(
    @Contextual
    @SerialName("downloadDir")
    val downloadDirectory: NormalizedRpcPath,
)
