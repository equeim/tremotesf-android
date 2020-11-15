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

package org.equeim.tremotesf.ui.addtorrent

import android.content.Context
import android.net.Uri

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.benjamin.Bdecoder
import org.equeim.tremotesf.Application
import org.equeim.tremotesf.ui.BaseTorrentFilesAdapter
import org.equeim.tremotesf.rpc.Rpc
import org.equeim.tremotesf.utils.Logger

import java.io.FileNotFoundException
import java.io.IOException


interface AddTorrentFileModel {
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

    data class ViewUpdateData(val parserStatus: ParserStatus,
                              val rpcStatus: Int,
                              val rpcStatusString: String,
                              val hasStoragePermission: Boolean = false)

    val parserStatus: StateFlow<ParserStatus>
    val viewUpdateData: Flow<ViewUpdateData>

    val fileData: ByteArray
    val rootDirectory: BaseTorrentFilesAdapter.Directory
    val torrentName: String
    val renamedFiles: MutableMap<String, String>

    fun onRequestPermissionResult(hasStoragePermission: Boolean)

    fun load(uri: Uri)

    fun getFilePriorities(): FilePriorities
}

class AddTorrentFileModelImpl : ViewModel(), AddTorrentFileModel, Logger {
    companion object {
        // 10 MiB
        const val MAX_FILE_SIZE = 10 * 1024 * 1024
    }

    override val parserStatus = MutableStateFlow(AddTorrentFileModel.ParserStatus.None)

    private val hasStoragePermission = MutableStateFlow(false)
    override val viewUpdateData = combine(parserStatus, Rpc.status, Rpc.statusString, hasStoragePermission) { parserStatus, rpcStatus, rpcStatusString, hasPermission -> AddTorrentFileModel.ViewUpdateData(parserStatus, rpcStatus, rpcStatusString, hasPermission) }

    override lateinit var fileData: ByteArray
        private set

    override val rootDirectory = BaseTorrentFilesAdapter.Directory()
    override val torrentName: String
        get() = rootDirectory.children.first().name

    override val renamedFiles = mutableMapOf<String, String>()

    private lateinit var files: List<BaseTorrentFilesAdapter.File>

    override fun onRequestPermissionResult(hasStoragePermission: Boolean) {
        this.hasStoragePermission.value = hasStoragePermission
    }

    override fun load(uri: Uri) {
        if (parserStatus.value == AddTorrentFileModel.ParserStatus.None) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.Loading
            viewModelScope.launch(Dispatchers.IO) {
                doLoad(uri, Application.instance)
            }
        }
    }

    override fun getFilePriorities(): AddTorrentFileModel.FilePriorities {
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
                else -> {
                }
            }
        }

        return AddTorrentFileModel.FilePriorities(unwantedFiles,
                                                  lowPriorityFiles,
                                                  highPriorityFiles)
    }

    private suspend fun doLoad(uri: Uri, context: Context) {
        val (status, fileData) = readFile(uri, context)
        if (status != AddTorrentFileModel.ParserStatus.Loading) {
            parserStatus.value = status
        } else {
            val parsed = parseFile(fileData!!)
            withContext(Dispatchers.Main) {
                if (parsed == null) {
                    parserStatus.value =
                            AddTorrentFileModel.ParserStatus.ParsingError
                } else {
                    this@AddTorrentFileModelImpl.fileData = fileData

                    val (rootDirectoryChild, files) = parsed

                    rootDirectoryChild.parentDirectory = rootDirectory
                    rootDirectory.addChild(rootDirectoryChild)

                    this@AddTorrentFileModelImpl.files = files

                    parserStatus.value = AddTorrentFileModel.ParserStatus.Loaded
                }
            }
        }
    }

    private fun readFile(uri: Uri, context: Context): Pair<AddTorrentFileModel.ParserStatus, ByteArray?> {
        try {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                error("openInputStream() returned null")
                return Pair(AddTorrentFileModel.ParserStatus.ReadingError, null)
            }
            stream.use {
                val size = stream.available()
                if (size > MAX_FILE_SIZE) {
                    error("Torrent file is too large")
                    return Pair(AddTorrentFileModel.ParserStatus.FileIsTooLarge, null)
                }
                return Pair(AddTorrentFileModel.ParserStatus.Loading, stream.readBytes())
            }
        } catch (error: FileNotFoundException) {
            error("File not found", error)
            return Pair(AddTorrentFileModel.ParserStatus.ReadingError, null)
        } catch (error: IOException) {
            error("Error reading torrent file", error)
            return Pair(AddTorrentFileModel.ParserStatus.ReadingError, null)
        } catch (error: SecurityException) {
            error("Error reading torrent file", error)
            return Pair(AddTorrentFileModel.ParserStatus.ReadingError, null)
        }
    }

    private fun parseFile(fileData: ByteArray): Pair<BaseTorrentFilesAdapter.Item, List<BaseTorrentFilesAdapter.File>>? {
        try {
            val torrentFileMap = Bdecoder(Charsets.UTF_8, fileData.inputStream()).decodeDict()
            return createTree(torrentFileMap)
        } catch (error: IllegalStateException) {
            error("Error parsing torrent file", error)
        } catch (error: ClassCastException) {
            error("Error parsing torrent file", error)
        } catch (error: OutOfMemoryError) {
            error("Error parsing torrent file", error)
        }
        return null
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
