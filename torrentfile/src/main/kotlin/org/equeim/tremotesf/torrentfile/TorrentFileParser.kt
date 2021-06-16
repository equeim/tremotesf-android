/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.torrentfile.torrentfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.bencode.Bencode
import org.equeim.tremotesf.torrentfile.TorrentFilesTree

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
    data class FilesTreeResult(
        val rootNode: TorrentFilesTree.Node,
        val files: List<TorrentFilesTree.Node>
    )

    suspend fun createFilesTree(fd: FileDescriptor): FilesTreeResult {
        return createFilesTree(parseFd(fd))
    }

    private suspend fun createFilesTree(torrentFile: TorrentFile): FilesTreeResult =
        withContext(Dispatchers.Default) {
            val rootNode = TorrentFilesTree.Node.createRootNode()
            val files = mutableListOf<TorrentFilesTree.Node>()

            val info = torrentFile.info

            if (info.files == null) {
                val node = rootNode.addFile(
                    0,
                    info.name,
                    info.length
                        ?: throw FileParseException("Field 'length' must not be null for single-file torrent"),
                    0,
                    TorrentFilesTree.Item.WantedState.Wanted,
                    TorrentFilesTree.Item.Priority.Normal
                )
                files.add(node)
            } else {
                val rootDirectoryNode = rootNode.addDirectory(info.name)
                for ((fileIndex, fileMap) in checkNotNull(info.files).withIndex()) {
                    var currentNode = rootDirectoryNode

                    val pathParts = fileMap.path
                    for ((partIndex, part) in pathParts.withIndex()) {
                        if (partIndex == pathParts.lastIndex) {
                            val node = currentNode.addFile(
                                fileIndex,
                                part,
                                fileMap.length,
                                0,
                                TorrentFilesTree.Item.WantedState.Wanted,
                                TorrentFilesTree.Item.Priority.Normal
                            )
                            files.add(node)
                        } else {
                            var childDirectoryNode = currentNode.getChildByItemNameOrNull(part)
                            if (childDirectoryNode == null) {
                                childDirectoryNode = currentNode.addDirectory(part)
                            }
                            currentNode = childDirectoryNode
                        }
                    }
                }

                rootDirectoryNode.initiallyCalculateFromChildrenRecursively()
            }

            FilesTreeResult(rootNode, files)
        }

    @Serializable
    private data class TorrentFile(val info: Info) {
        @Serializable
        data class Info(
            val files: List<File>? = null,
            val length: Long? = null,
            val name: String
        )

        @Serializable
        data class File(val length: Long, val path: List<String>)
    }

    private suspend fun parseFd(fd: FileDescriptor): TorrentFile =
        parseFile(FileInputStream(fd).buffered())

    private suspend fun parseFile(inputStream: InputStream): TorrentFile =
        withContext(Dispatchers.IO) {
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
