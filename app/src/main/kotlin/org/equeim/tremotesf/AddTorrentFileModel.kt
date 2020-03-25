/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import java.io.FileNotFoundException
import java.io.IOException

import android.content.Context
import android.net.Uri

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.benjamin.Bdecoder

import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.NonNullMutableLiveData


class AddTorrentFileModel : ViewModel(), Logger {
    companion object {
        // 10 MiB
        const val MAX_FILE_SIZE = 10 * 1024 * 1024
    }

    enum class ParserStatus {
        None,
        Loading,
        FileIsTooLarge,
        ReadingError,
        ParsingError,
        Loaded
    }

    data class FilePriorities(val unwantedFiles: List<Int>,
                              val lowPriorityFiles: List<Int>,
                              val highPriorityFiles: List<Int>)


    val status = NonNullMutableLiveData(ParserStatus.None)

    lateinit var fileData: ByteArray
        private set

    val rootDirectory = BaseTorrentFilesAdapter.Directory()
    val torrentName: String
        get() = rootDirectory.children.first().name

    val renamedFiles = mutableMapOf<String, String>()

    private lateinit var files: List<BaseTorrentFilesAdapter.File>

    fun load(uri: Uri) {
        if (status.value == ParserStatus.None) {
            status.value = ParserStatus.Loading
            viewModelScope.launch(Dispatchers.IO) {
                doLoad(uri, Application.instance)
            }
        }
    }

    fun getFilePriorities(): FilePriorities {
        val unwantedFiles = mutableListOf<Int>()
        val lowPriorityFiles = mutableListOf<Int>()
        val highPriorityFiles = mutableListOf<Int>()

        for (file in files) {
            val id = file.id
            if (file.wantedState == BaseTorrentFilesAdapter.Item.WantedState.Unwanted) {
                unwantedFiles.add(id)
            }
            when (file.priority) {
                BaseTorrentFilesAdapter.Item.Priority.Low -> lowPriorityFiles.add(id)
                BaseTorrentFilesAdapter.Item.Priority.High -> highPriorityFiles.add(id)
                else -> {}
            }
        }

        return FilePriorities(unwantedFiles,
                              lowPriorityFiles,
                              highPriorityFiles)
    }

    private suspend fun doLoad(uri: Uri, context: Context) {
        val (status, fileData) = readFile(uri, context)
        if (status != ParserStatus.Loading) {
            withContext(Dispatchers.Main) {
                this@AddTorrentFileModel.status.value = status
            }
        } else {
            val parsed = parseFile(fileData!!)
            withContext(Dispatchers.Main) {
                if (parsed == null) {
                    this@AddTorrentFileModel.status.value = ParserStatus.ParsingError
                } else {
                    this@AddTorrentFileModel.fileData = fileData

                    val (rootDirectoryChild, files) = parsed

                    rootDirectoryChild.parentDirectory = rootDirectory
                    rootDirectory.addChild(rootDirectoryChild)

                    this@AddTorrentFileModel.files = files

                    this@AddTorrentFileModel.status.value = ParserStatus.Loaded
                }
            }
        }
    }

    private fun readFile(uri: Uri, context: Context): Pair<ParserStatus, ByteArray?> {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                error("openInputStream() returned null")
                return Pair(ParserStatus.ReadingError, null)
            }
            stream.use {
                val size = stream.available()
                if (size > MAX_FILE_SIZE) {
                    error("Torrent file is too large")
                    return Pair(ParserStatus.FileIsTooLarge, null)
                }
                return Pair(ParserStatus.Loading, stream.readBytes())
            }
        } catch (error: FileNotFoundException) {
            error("File not found", error)
            return Pair(ParserStatus.ReadingError, null)
        } catch (error: IOException) {
            error("Error reading torrent file", error)
            return Pair(ParserStatus.ReadingError, null)
        } catch (error: SecurityException) {
            error("Error reading torrent file", error)
            return Pair(ParserStatus.ReadingError, null)
        }
    }

    private fun parseFile(fileData: ByteArray): Pair<BaseTorrentFilesAdapter.Item, List<BaseTorrentFilesAdapter.File>>? {
        return try {
            val torrentFileMap = Bdecoder(Charsets.UTF_8, fileData.inputStream()).decodeDict()
            createTree(torrentFileMap)
        } catch (error: IllegalStateException) {
            error("Error parsing torrent file", error)
            null
        } catch (error: ClassCastException) {
            error("Error parsing torrent file", error)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createTree(torrentFileMap: Map<String, Any>): Pair<BaseTorrentFilesAdapter.Item, List<BaseTorrentFilesAdapter.File>> {
        val rootDirectoryChild: BaseTorrentFilesAdapter.Item
        val files = mutableListOf<BaseTorrentFilesAdapter.File>()

        val infoMap = torrentFileMap["info"] as Map<String, Any>

        if (infoMap.contains("files")) {
            val torrentDirectory = BaseTorrentFilesAdapter.Directory(0,
                                                                     null,
                                                                     infoMap["name"] as String)

            val filesMaps = infoMap["files"] as List<Map<String, Any>>
            for ((fileIndex, fileMap) in filesMaps.withIndex()) {
                var directory = torrentDirectory

                val pathParts = fileMap["path"] as List<String>
                for ((partIndex, part) in pathParts.withIndex()) {
                    if (partIndex == pathParts.lastIndex) {
                        val file = BaseTorrentFilesAdapter.File(directory.children.size,
                                                                directory,
                                                                part,
                                                                fileMap["length"] as Long,
                                                                fileIndex)
                        directory.addChild(file)
                        files.add(file)
                    } else {
                        var childDirectory = directory.childrenMap[part]
                                as BaseTorrentFilesAdapter.Directory?
                        if (childDirectory == null) {
                            childDirectory = BaseTorrentFilesAdapter.Directory(directory.children.size,
                                                                               directory,
                                                                               part)
                            directory.addChild(childDirectory)
                        }
                        directory = childDirectory
                    }
                }
            }

            rootDirectoryChild = torrentDirectory
        } else {
            val file = BaseTorrentFilesAdapter.File(0,
                                                    null,
                                                    infoMap["name"] as String,
                                                    infoMap["length"] as Long,
                                                    0)
            files.add(file)
            rootDirectoryChild = file
        }

        rootDirectoryChild.setWanted(true)

        return Pair(rootDirectoryChild, files)
    }
}
