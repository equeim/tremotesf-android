// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentFiles
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentFilesPriority
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentFilesWanted
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.torrentfile.buildTorrentFilesTree
import timber.log.Timber

class RpcTorrentFilesTree(
    private val torrentHashString: String,
    parentScope: CoroutineScope,
    private val onTorrentRenamed: () -> Unit
) : TorrentFilesTree(parentScope) {
    companion object {
        private fun Item.updatedFromIfNeeded(file: TorrentFiles.File, fileStats: TorrentFiles.FileStats): Item? {
            val newName = file.pathSegments.lastOrNull().orEmpty()
            val newCompletedSize = fileStats.completedSize.bytes
            val newWantedState = Item.WantedState.fromBoolean(fileStats.wanted)
            val newPriority = fileStats.priority.toTreeItemPriority()
            return if (newName != name || newCompletedSize != completedSize || newWantedState != wantedState || newPriority != priority) {
                copy(
                    name = newName,
                    completedSize = newCompletedSize,
                    wantedState = newWantedState,
                    priority = newPriority
                )
            } else {
                null
            }
        }

        private fun TorrentFiles.FilePriority.toTreeItemPriority(): Item.Priority {
            return when (this) {
                TorrentFiles.FilePriority.Low -> Item.Priority.Low
                TorrentFiles.FilePriority.High -> Item.Priority.High
                TorrentFiles.FilePriority.Normal -> Item.Priority.Normal
            }
        }

        private fun Item.Priority.toTorrentFilePriority(): TorrentFiles.FilePriority {
            return when (this) {
                Item.Priority.Low -> TorrentFiles.FilePriority.Low
                Item.Priority.Normal -> TorrentFiles.FilePriority.Normal
                Item.Priority.High -> TorrentFiles.FilePriority.High
                else -> TorrentFiles.FilePriority.Normal
            }
        }
    }

    private var files: List<FileNode> = emptyList()

    val isEmpty: Boolean
        get() = files.isEmpty()

    override fun onSetFilesWanted(ids: List<Int>, wanted: Boolean) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_files_wanted_error) {
            GlobalRpcClient.setTorrentFilesWanted(
                torrentHashString,
                ids,
                wanted
            )
        }
    }

    override fun onSetFilesPriority(ids: List<Int>, priority: Item.Priority) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_files_priority_error) {
            GlobalRpcClient.setTorrentFilesPriority(
                torrentHashString,
                ids,
                priority.toTorrentFilePriority()
            )
        }
    }

    override fun onFileRenamed(
        path: NodePath,
        originalNamePath: String,
        newName: String
    ) {
        scope.launch {
            GlobalRpcClient.awaitBackgroundRpcRequest(R.string.file_rename_error) {
                renameTorrentFile(torrentHashString, originalNamePath, newName)
            }
            if (path.indices.size == 1) {
                onTorrentRenamed()
            }
        }
    }

    suspend fun createTree(rpcFiles: TorrentFiles, savedStateHandle: SavedStateHandle) {
        try {
            val coroutineContext = currentCoroutineContext()
            val (rootNode, files) = withContext(dispatcher) {
                buildTorrentFilesTree {
                    for (index in rpcFiles.files.indices) {
                        coroutineContext.ensureActive()

                        val file = rpcFiles.files[index]
                        val fileStats = rpcFiles.fileStats[index]

                        addFile(
                            fileId = index,
                            path = file.pathSegments.toList(),
                            size = file.size.bytes,
                            completedSize = fileStats.completedSize.bytes,
                            wantedState = Item.WantedState.fromBoolean(fileStats.wanted),
                            priority = fileStats.priority.toTreeItemPriority()
                        )
                    }
                }
            }
            this.files = files
            init(rootNode, savedStateHandle)
        } catch (e: IllegalArgumentException) {
            if (BuildConfig.DEBUG) {
                throw e
            } else {
                Timber.e(e, "Failed to build torrent files tree")
            }
        }
    }

    suspend fun updateTree(rpcFiles: TorrentFiles) = withContext(dispatcher) {
        val files = this@RpcTorrentFilesTree.files
        if (files.size != rpcFiles.files.size) {
            Timber.e("New files have different count")
            return@withContext
        }
        val changedFiles = sequence {
            for (i in files.indices) {
                ensureActive()

                val fileNode = files[i]
                val rpcFile = rpcFiles.files[i]
                val rpcFileStats = rpcFiles.fileStats[i]

                val newItem = fileNode.item.updatedFromIfNeeded(rpcFile, rpcFileStats)
                if (newItem != null) {
                    fileNode.item = newItem
                    yield(fileNode)
                }
            }
        }
        val recalculated = recalculateNodesAndTheirParents(changedFiles)
        if (recalculated.contains(currentNode)) {
            updateItemsWithSorting()
        }
    }

    @MainThread
    override fun reset() {
        super.reset()
        files = emptyList()
    }
}
