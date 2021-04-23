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

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.TorrentFilesTree
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.torrentfile.FileIsTooLargeException
import org.equeim.tremotesf.data.torrentfile.FileParseException
import org.equeim.tremotesf.data.torrentfile.FileReadException
import org.equeim.tremotesf.data.torrentfile.TorrentFile
import org.equeim.tremotesf.data.torrentfile.TorrentFileParser
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.savedState
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
                              val rpcStatus: Rpc.Status,
                              val hasStoragePermission: Boolean)

    var rememberedPagerItem: Int

    val uri: Uri
    val needStoragePermission: Boolean

    val storagePermissionHelper: RuntimePermissionHelper

    val parserStatus: StateFlow<ParserStatus>
    val viewUpdateData: Flow<ViewUpdateData>

    val filesTree: TorrentFilesTree
    val torrentName: String
    val renamedFiles: MutableMap<String, String>

    fun detachFd(): Int
    fun getFilePriorities(): FilePriorities
}

class AddTorrentFileModelImpl(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application), AddTorrentFileModel, Logger {
    override var rememberedPagerItem: Int by savedState(savedStateHandle, -1)

    override val uri: Uri by savedState(savedStateHandle, Uri.EMPTY)
    override val needStoragePermission = uri.scheme == ContentResolver.SCHEME_FILE

    override val storagePermissionHelper = RuntimePermissionHelper(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        R.string.storage_permission_rationale_torrent
    )

    override val parserStatus = MutableStateFlow(AddTorrentFileModel.ParserStatus.None)

    override val viewUpdateData = combine(parserStatus, Rpc.status, storagePermissionHelper.permissionGranted) { parserStatus, rpcStatus, hasPermission -> AddTorrentFileModel.ViewUpdateData(parserStatus, rpcStatus, hasPermission) }

    private var fd: AssetFileDescriptor? = null

    override val filesTree = TorrentFilesTree(viewModelScope)
    override val torrentName: String
        get() = filesTree.rootNode.children.first().item.name

    override val renamedFiles = mutableMapOf<String, String>()

    private lateinit var files: List<TorrentFilesTree.Item>

    init {
        if (needStoragePermission) {
            viewModelScope.launch {
                storagePermissionHelper.permissionGranted.first { it }
                load()
            }
        } else {
            load()
        }
    }

    override fun onCleared() {
        fd?.close()
    }

    private fun load() {
        info("load: loading $uri")
        if (parserStatus.value == AddTorrentFileModel.ParserStatus.None) {
            parserStatus.value = AddTorrentFileModel.ParserStatus.Loading
            viewModelScope.launch {
                doLoad(uri, getApplication())
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
            val id = file.fileId
            if (file.wantedState == TorrentFilesTree.Item.WantedState.Unwanted) {
                unwantedFiles.add(id)
            }
            when (file.priority) {
                TorrentFilesTree.Item.Priority.Low -> lowPriorityFiles.add(id)
                TorrentFilesTree.Item.Priority.High -> highPriorityFiles.add(id)
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
                val (rootNode, files) = createTree(torrentFile.info)
                withContext(Dispatchers.Main) {
                    this@AddTorrentFileModelImpl.fd = fd
                    this@AddTorrentFileModelImpl.files = files
                    filesTree.init(rootNode, savedStateHandle)
                    parserStatus.value = AddTorrentFileModel.ParserStatus.Loaded
                }
            }
        }
    }

    private fun createTree(torrentFileInfo: TorrentFile.Info): Pair<TorrentFilesTree.Node, List<TorrentFilesTree.Item>> {
        val rootNode = TorrentFilesTree.Node.createRootNode()
        val files = mutableListOf<TorrentFilesTree.Item>()

        if (torrentFileInfo.files == null) {
            val node = rootNode.addFile(0,
                                        torrentFileInfo.name,
                                        torrentFileInfo.length!!,
                                        0,
                                        TorrentFilesTree.Item.WantedState.Wanted,
                                        TorrentFilesTree.Item.Priority.Normal)
            files.add(node.item)
        } else {
            val rootDirectoryNode = rootNode.addDirectory(torrentFileInfo.name)
            for ((fileIndex, fileMap) in torrentFileInfo.files.withIndex()) {
                var currentNode = rootDirectoryNode

                val pathParts = fileMap.path
                for ((partIndex, part) in pathParts.withIndex()) {
                    if (partIndex == pathParts.lastIndex) {
                        val node = currentNode.addFile(fileIndex,
                                                       part,
                                                       fileMap.length,
                                                       0,
                                                       TorrentFilesTree.Item.WantedState.Wanted,
                                                       TorrentFilesTree.Item.Priority.Normal)
                        files.add(node.item)
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

        return Pair(rootNode, files)
    }
}
