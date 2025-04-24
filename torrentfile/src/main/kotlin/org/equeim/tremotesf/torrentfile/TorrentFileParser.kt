// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.bencode.Bencode
import org.equeim.bencode.ByteRange
import org.equeim.bencode.ReportByteRange
import org.equeim.tremotesf.common.DefaultTremotesfDispatchers
import org.equeim.tremotesf.common.TremotesfDispatchers
import timber.log.Timber
import java.io.EOFException
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.math.min


class FileReadException(cause: Throwable) : Exception(cause)
class FileIsTooLargeException : Exception()
class FileParseException : Exception {
    constructor(cause: Throwable) : super(cause)
    constructor(message: String) : super(message)
}

@ConsistentCopyVisibility
data class TorrentParseResult internal constructor(
    val infoHashV1: String,
    internal val torrentFile: TorrentFileParser.TorrentFile,
) {
    val name: String
        get() = torrentFile.info.nameOfDirectoryOrSingleFile

    val trackers: List<Set<String>>
        get() = torrentFile.trackersAnnounceUrls
            ?: torrentFile.singleTrackerAnnounceUrl?.let { listOf(setOf(it)) }.orEmpty()
}

object TorrentFileParser {
    suspend fun parseTorrentFile(fd: FileDescriptor): TorrentParseResult {
        val fileInput = FileInputStream(fd)
        return parseTorrentFile(fileInput)
    }

    @VisibleForTesting
    internal suspend fun parseTorrentFile(
        inputStream: FileInputStream,
        dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers,
    ): TorrentParseResult =
        withContext(dispatchers.IO) {
            if (inputStream.available() > MAX_FILE_SIZE) {
                Timber.e("File is too large")
                throw FileIsTooLargeException()
            }
            try {
                val (torrentFile, infoByteRange) = Bencode.decode<TorrentFile>(inputStream.buffered())
                infoByteRange
                    ?: throw SerializationException("Failed to determine info dictionary byte range")
                TorrentParseResult(computeInfoHashV1(inputStream, infoByteRange), torrentFile)
            } catch (error: IOException) {
                Timber.e(error, "Failed to read file")
                throw FileReadException(error)
            } catch (error: SerializationException) {
                Timber.e(error, "Failed to parse bencode structure")
                throw FileParseException(error)
            }
        }

    @Serializable
    @VisibleForTesting
    internal data class TorrentFile(
        @ReportByteRange
        @SerialName("info")
        val info: Info,
        @SerialName("announce")
        val singleTrackerAnnounceUrl: String? = null,
        @SerialName("announce-list")
        val trackersAnnounceUrls: List<Set<String>>? = null
    ) {
        @Serializable
        data class Info(
            @SerialName("files")
            val files: List<File>? = null,
            @SerialName("length")
            val singleFileSize: Long? = null,
            @SerialName("name")
            val nameOfDirectoryOrSingleFile: String,
        )

        @Serializable
        data class File(
            @SerialName("length")
            val size: Long,
            @SerialName("path")
            val pathSegments: List<String>
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun computeInfoHashV1(inputStream: FileInputStream, byteRange: ByteRange): String {
        try {
            inputStream.channel.position(byteRange.offset.toLong())
            val digestInputStream =
                DigestInputStream(inputStream, MessageDigest.getInstance("SHA1"))
            var remaining = byteRange.length
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val len = min(buffer.size, remaining)
                val n = digestInputStream.read(buffer, 0, len)
                if (n == -1) throw EOFException()
                remaining -= n
            }
            return digestInputStream.messageDigest.digest().toHexString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to compute info hash")
            throw e
        }
    }

    suspend fun createFilesTree(parseResult: TorrentParseResult): TorrentFilesTreeBuildResult {
        return createFilesTree(parseResult.torrentFile)
    }

    @VisibleForTesting
    internal suspend fun createFilesTree(
        torrentFile: TorrentFile,
        dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers,
    ): TorrentFilesTreeBuildResult =
        withContext(dispatchers.Default) {
            runCatching {
                buildTorrentFilesTree {
                    val info = torrentFile.info
                    if (info.files == null) {
                        addFile(
                            0,
                            listOf(info.nameOfDirectoryOrSingleFile),
                            info.singleFileSize
                                ?: throw FileParseException("Field 'length' must not be null for single-file torrent"),
                            0,
                            TorrentFilesTree.Item.WantedState.Wanted,
                            TorrentFilesTree.Item.Priority.Normal
                        )
                    } else {
                        val fullPath = mutableListOf(info.nameOfDirectoryOrSingleFile)
                        info.files.forEachIndexed { index, file ->
                            ensureActive()

                            if (fullPath.size > 1) fullPath.subList(1, fullPath.size).clear()
                            fullPath.addAll(file.pathSegments)
                            addFile(
                                index,
                                fullPath,
                                file.size,
                                0,
                                TorrentFilesTree.Item.WantedState.Wanted,
                                TorrentFilesTree.Item.Priority.Normal
                            )
                        }
                    }
                }
            }.getOrElse {
                Timber.e(it, "Failed to build file tree")
                throw if (it is FileParseException) it else FileParseException(it)
            }
        }

    // 10 MiB
    private const val MAX_FILE_SIZE = 20 * 1024 * 1024
}
