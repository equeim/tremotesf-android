// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestContext
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsDownloadDirectories(): List<NormalizedRpcPath> =
    if (checkServerCapabilities(
            force = false,
            RpcRequestContext(RpcMethod.TorrentGet, "getTorrentsDownloadDirectories")
        ).hasTableMode
    ) {
        performRequest<RpcResponse<TorrentsDownloadDirectoriesTableResponseArguments>>(
            org.equeim.tremotesf.rpc.requests.TORRENTS_DOWNLOAD_DIRECTORIES_TABLE_REQUEST,
            "getTorrentsDownloadDirectories"
        ).arguments.torrents
    } else {
        performRequest<RpcResponse<TorrentsDownloadDirectoriesObjectsResponseArguments>>(
            org.equeim.tremotesf.rpc.requests.TORRENTS_DOWNLOAD_DIRECTORIES_OBJECTS_REQUEST,
            "getTorrentsDownloadDirectories"
        ).arguments.torrents
    }.map { it.downloadDirectory }

@Serializable
private data class TorrentsDownloadDirectoriesObjectsRequestArguments(
    @SerialName("fields")
    val fields: List<String> = listOf("downloadDir"),
)

private val TORRENTS_DOWNLOAD_DIRECTORIES_OBJECTS_REQUEST =
    RpcRequestBody(RpcMethod.TorrentGet, TorrentsDownloadDirectoriesObjectsRequestArguments())

@Serializable
private data class TorrentsDownloadDirectoriesObjectsResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentsDownloadDirectoriesFields>,
)

@Serializable
private data class TorrentsDownloadDirectoriesTableRequestArguments(
    @SerialName("fields")
    val fields: List<String> = listOf("downloadDir"),
    @SerialName("format")
    val format: String = "table",
)

private val TORRENTS_DOWNLOAD_DIRECTORIES_TABLE_REQUEST =
    RpcRequestBody(RpcMethod.TorrentGet, TorrentsDownloadDirectoriesTableRequestArguments())

@Serializable
private data class TorrentsDownloadDirectoriesTableResponseArguments(
    @Serializable(TorrentsDownloadDirectoriesTableSerializer::class)
    @SerialName("torrents")
    val torrents: List<TorrentsDownloadDirectoriesFields>,
)

@Serializable
private data class TorrentsDownloadDirectoriesFields(
    @Contextual
    @SerialName("downloadDir")
    val downloadDirectory: NormalizedRpcPath,
)

private object TorrentsDownloadDirectoriesTableSerializer :
    TorrentsTableSerializer<TorrentsDownloadDirectoriesFields>(serializer())
