// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.RpcEnum
import org.equeim.tremotesf.rpc.requests.RpcResponse

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentFiles(hashString: String): TorrentFiles? =
    performRequest<RpcResponse<TorrentFilesResponseArguments>, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentGet,
        TorrentFilesRequestArguments(hashString),
        "getTorrentFiles"
    ).arguments.torrents.firstOrNull()

@Serializable
data class TorrentFiles(
    @SerialName("files")
    val files: List<File>,
    @SerialName("fileStats")
    val fileStats: List<FileStats>,
) {
    @Serializable
    data class File(
        @SerialName("name")
        val name: String,
        @SerialName("length")
        val size: FileSize,
    )

    @Serializable
    data class FileStats(
        @SerialName("bytesCompleted")
        val completedSize: FileSize,
        @SerialName("wanted")
        val wanted: Boolean,
        @SerialName("priority")
        val priority: FilePriority,
    )

    @Serializable(FilePriority.Serializer::class)
    enum class FilePriority(override val rpcValue: Int) : RpcEnum {
        Low(-1),
        Normal(0),
        High(1);

        internal object Serializer : RpcEnum.Serializer<FilePriority>(FilePriority::class)
    }
}

@Serializable
private data class TorrentFilesRequestArguments(
    @SerialName("ids")
    val ids: List<String>,

    @SerialName("fields")
    val fields: List<String> = listOf("files", "fileStats"),
) {
    constructor(hashString: String) : this(ids = listOf(hashString))
}

@Serializable
private data class TorrentFilesResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentFiles>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
        torrents.firstOrNull()?.let {
            if (it.files.size != it.fileStats.size) {
                throw SerializationException("'files' and 'fileStats' arrays must have the same size")
            }
        }
    }
}
