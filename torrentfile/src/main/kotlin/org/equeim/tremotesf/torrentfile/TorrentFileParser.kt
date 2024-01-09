// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.bencode.Bencode
import org.equeim.tremotesf.common.DefaultTremotesfDispatchers
import org.equeim.tremotesf.common.TremotesfDispatchers

import timber.log.Timber

import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream


class FileReadException(cause: Throwable) : Exception(cause)
class FileIsTooLargeException : Exception()
class FileParseException : Exception {
    constructor(cause: Throwable) : super(cause)
    constructor(message: String) : super(message)
}

object TorrentFileParser {
    suspend fun createFilesTree(fd: FileDescriptor): TorrentFilesTreeBuildResult {
        return createFilesTree(parseFd(fd))
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
                            listOf(info.name),
                            info.length
                                ?: throw FileParseException("Field 'length' must not be null for single-file torrent"),
                            0,
                            TorrentFilesTree.Item.WantedState.Wanted,
                            TorrentFilesTree.Item.Priority.Normal
                        )
                    } else {
                        val fullPath = mutableListOf(info.name)
                        info.files.forEachIndexed { index, file ->
                            ensureActive()

                            if (fullPath.size > 1) fullPath.subList(1, fullPath.size).clear()
                            fullPath.addAll(file.path)
                            addFile(
                                index,
                                fullPath,
                                file.length,
                                0,
                                TorrentFilesTree.Item.WantedState.Wanted,
                                TorrentFilesTree.Item.Priority.Normal
                            )
                        }
                    }
                }
            }.getOrElse {
                throw if (it is FileParseException) it else FileParseException(it)
            }
        }

    @Serializable
    @VisibleForTesting
    internal data class TorrentFile(val info: Info) {
        @Serializable
        data class Info(
            val files: List<File>? = null,
            val length: Long? = null,
            val name: String,
        )

        @Serializable
        data class File(val length: Long, val path: List<String>)
    }

    private suspend fun parseFd(fd: FileDescriptor): TorrentFile =
        parseFile(FileInputStream(fd).buffered())

    @VisibleForTesting
    internal suspend fun parseFile(
        inputStream: InputStream,
        dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers,
    ): TorrentFile =
        withContext(dispatchers.IO) {
            @Suppress("BlockingMethodInNonBlockingContext")
            if (inputStream.available() > MAX_FILE_SIZE) {
                Timber.e("File is too large")
                throw FileIsTooLargeException()
            }
            try {
                Bencode.decode(inputStream)
            } catch (error: IOException) {
                Timber.e(error, "Failed to read file")
                throw FileReadException(error)
            } catch (error: SerializationException) {
                Timber.e(error, "Failed to parse bencode structure")
                throw FileParseException(error)
            }
        }

    // 10 MiB
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024
}
