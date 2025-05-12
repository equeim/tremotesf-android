// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.SingleTorrentRequestArguments
import org.equeim.tremotesf.rpc.requests.SingleTorrentResponseArguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentFiles(hashString: String): TorrentFiles? =
    performRequest<SingleTorrentResponseArguments<TorrentFiles>, _>(
        RpcMethod.TorrentGet,
        SingleTorrentRequestArguments(hashString, listOf("files", "fileStats")),
        "getTorrentFiles"
    ).arguments.torrents.firstOrNull()

@Serializable
data class TorrentFiles(
    @SerialName("files")
    val files: List<File>,
    @SerialName("fileStats")
    val fileStats: List<FileStats>,
) {
    init {
        if (files.size != fileStats.size) {
            throw SerializationException("'files' and 'fileStats' arrays must have the same size")
        }
    }

    @Serializable
    data class File(
        @SerialName("name")
        val path: String,
        @SerialName("length")
        val size: FileSize,
    ) {
        val pathSegments: Sequence<String>
            get() = path.splitToSequence('/').filter { it.isNotEmpty() }
    }

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
