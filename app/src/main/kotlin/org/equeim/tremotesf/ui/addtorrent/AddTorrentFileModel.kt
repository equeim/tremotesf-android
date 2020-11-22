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
import android.content.res.AssetFileDescriptor
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

import org.equeim.tremotesf.Application
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.torrentfile.FileIsTooLargeException
import org.equeim.tremotesf.data.torrentfile.FileParseException
import org.equeim.tremotesf.data.torrentfile.FileReadException
import org.equeim.tremotesf.data.torrentfile.TorrentFile
import org.equeim.tremotesf.data.torrentfile.TorrentFileParser
import org.equeim.tremotesf.ui.BaseTorrentFilesAdapter
import org.equeim.tremotesf.utils.Logger


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

    val rootDirectory: BaseTorrentFilesAdapter.Directory
    val torrentName: String
    val renamedFiles: MutableMap<String, String>

    fun onRequestPermissionResult(hasStoragePermission: Boolean)
    fun load(uri: Uri)
    fun detachFd(): Int
    fun getFilePriorities(): FilePriorities
}

class AddTorrentFileModelImpl : ViewModel(), AddTorrentFileModel, Logger {
    override val parserStatus = MutableStateFlow(AddTorrentFileModel.ParserStatus.None)

    private val hasStoragePermission = MutableStateFlow(false)
    override val viewUpdateData = combine(parserStatus, Rpc.status, Rpc.statusString, hasStoragePermission) { parserStatus, rpcStatus, rpcStatusString, hasPermission -> AddTorrentFileModel.ViewUpdateData(parserStatus, rpcStatus, rpcStatusString, hasPermission) }

    private var fd: AssetFileDescriptor? = null

    override val rootDirectory = BaseTorrentFilesAdapter.Directory()
    override val torrentName: String
        get() = rootDirectory.children.first().name

    override val renamedFiles = mutableMapOf<String, String>()

    private lateinit var files: List<BaseTorrentFilesAdapter.File>

    override fun onCleared() {
        fd?.close()
    }

    override fun onRequestPermissionResult(hasStoragePermission: Boolean) {
        this.hasStoragePermission.value = hasStoragePermission
    }

    override fun load(uri: Uri) {
        if (parserStatus.value == AddTorrentFileModel.ParserStatus.None) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.Loading
            viewModelScope.launch {
                doLoad(uri, Application.instance)
            }
        }
    }

    override fun detachFd(): Int {
        return fd!!.parcelFileDescriptor.detachFd().also {
            fd = null
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
        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            val fd = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")!!
            } catch (error: Exception) {
                error("Failed to open file descriptor")
                parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
                return@withContext
            }

            val torrentFile = try {
                TorrentFileParser.parse(fd.fileDescriptor)
            } catch (error: FileReadException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ReadingError
                fd.close()
                return@withContext
            } catch (error: FileIsTooLargeException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.FileIsTooLarge
                fd.close()
                return@withContext
            } catch (error: FileParseException) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ParsingError
                fd.close()
                return@withContext
            }

            if (torrentFile.info.files == null && torrentFile.info.length == null) {
                parserStatus.value = AddTorrentFileModel.ParserStatus.ParsingError
                fd.close()
                return@withContext
            }

            withContext(Dispatchers.Default) {
                val (rootDirectoryChild, files) = createTree(torrentFile.info)
                withContext(Dispatchers.Main) {
                    this@AddTorrentFileModelImpl.fd = fd
                    rootDirectoryChild.parentDirectory = rootDirectory
                    rootDirectory.addChild(rootDirectoryChild)
                    this@AddTorrentFileModelImpl.files = files
                    parserStatus.value = AddTorrentFileModel.ParserStatus.Loaded
                }
            }
        }
    }

    private fun createTree(torrentFileInfo: TorrentFile.Info): Pair<BaseTorrentFilesAdapter.Item, List<BaseTorrentFilesAdapter.File>> {
        val rootDirectoryChild: BaseTorrentFilesAdapter.Item
        val files = mutableListOf<BaseTorrentFilesAdapter.File>()

        if (torrentFileInfo.files == null) {
            rootDirectoryChild = BaseTorrentFilesAdapter.File(0, null, torrentFileInfo.name, torrentFileInfo.length!!, 0)
            files.add(rootDirectoryChild)
        } else {
            val rootDirectory = BaseTorrentFilesAdapter.Directory(0,
                                                                  null,
                                                                  torrentFileInfo.name)
            rootDirectoryChild = rootDirectory
            for ((fileIndex, fileMap) in torrentFileInfo.files.withIndex()) {
                var directory = rootDirectory

                val pathParts = fileMap.path
                for ((partIndex, part) in pathParts.withIndex()) {
                    if (partIndex == pathParts.lastIndex) {
                        val file = BaseTorrentFilesAdapter.File(directory.children.size,
                                                                directory,
                                                                part,
                                                                fileMap.length,
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
        }

        rootDirectoryChild.setWanted(true)

        return Pair(rootDirectoryChild, files)
    }
}
