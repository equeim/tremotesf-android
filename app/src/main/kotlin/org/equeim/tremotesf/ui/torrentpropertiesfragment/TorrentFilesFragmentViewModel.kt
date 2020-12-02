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

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.tremotesf.data.TorrentFilesTree
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import java.util.LinkedHashSet
import kotlin.properties.Delegates

class TorrentFilesFragmentViewModel(torrent: Torrent?, private val savedStateHandle: SavedStateHandle) : ViewModel() {
    enum class State {
        None,
        Loading,
        CreatingTree,
        TreeCreated
    }

    var torrent by Delegates.observable<Torrent?>(null) { _, oldTorrent, torrent ->
        if (torrent != oldTorrent) {
            if (torrent == null) {
                oldTorrent?.filesEnabled = false
                reset()
            } else if (oldTorrent == null) {
                torrent.filesEnabled = true
                _state.value = State.Loading
            }
        }
    }

    private val _state = MutableStateFlow(State.None)
    val state: StateFlow<State> by ::_state

    val filesTree = RpcTorrentFilesTree(this, viewModelScope)

    init {
        this.torrent = torrent
        viewModelScope.launch { Rpc.torrentFilesUpdatedEvents.collect(::onTorrentFilesUpdated) }
        viewModelScope.launch {
            Rpc.torrentFileRenamedEvents.collect { (torrentId, filePath, newName) ->
                if (torrentId == torrent?.id) {
                    filesTree.renameFile(filePath, newName)
                }
            }
        }
    }

    private fun createTree(rpcFiles: List<TorrentFile>) {
        if (rpcFiles.isEmpty()) {
            _state.value = State.TreeCreated
        } else {
            _state.value = State.CreatingTree
            filesTree.createTree(rpcFiles)
        }
    }

    fun treeCreated(rootNode: TorrentFilesTree.Node) {
        filesTree.init(rootNode, savedStateHandle)
        _state.value = State.TreeCreated
    }

    private fun reset() {
        if (state.value != State.None) {
            filesTree.reset()
            _state.value = State.None
        }
    }

    private fun onTorrentFilesUpdated(data: Rpc.TorrentFilesUpdatedData) {
        val (torrentId, changedFiles) = data
        if (torrentId == torrent?.id) {
            when (state.value) {
                State.TreeCreated -> {
                    if (filesTree.isEmpty && changedFiles.isNotEmpty()) {
                        // Torrent's metadata was downloaded
                        createTree(changedFiles)
                    } else {
                        filesTree.updateTree(changedFiles)
                    }
                }
                State.Loading -> createTree(changedFiles)
                else -> {}
            }
        }
    }

    override fun onCleared() {
        torrent = null
        filesTree.dispatcher.close()
    }
}

class RpcTorrentFilesTree(private val model: TorrentFilesFragmentViewModel, parentScope: CoroutineScope) : TorrentFilesTree(parentScope) {
    companion object {
        private fun Item.updatedFrom(rpcFile: TorrentFile): Item {
            return copy(name = name,
                        completedSize = rpcFile.completedSize,
                        wantedState = Item.WantedState.fromBoolean(rpcFile.wanted),
                        priority = fromTorrentFilePriority(rpcFile.priority))
        }

        private fun fromTorrentFilePriority(priority: Int): Item.Priority {
            return when (priority) {
                TorrentFile.Priority.LowPriority -> Item.Priority.Low
                TorrentFile.Priority.NormalPriority -> Item.Priority.Normal
                TorrentFile.Priority.HighPriority -> Item.Priority.High
                else -> Item.Priority.Normal
            }
        }

        private fun Item.Priority.toTorrentFilePriority(): Int {
            return when (this) {
                Item.Priority.Low -> TorrentFile.Priority.LowPriority
                Item.Priority.Normal -> TorrentFile.Priority.NormalPriority
                Item.Priority.High -> TorrentFile.Priority.HighPriority
                else -> TorrentFile.Priority.NormalPriority
            }
        }
    }

    @Volatile
    private var files: List<Node> = emptyList()

    val isEmpty: Boolean
        get() = files.isEmpty()

    override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
        model.torrent?.setFilesWanted(ids, wanted)
    }

    override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
        model.torrent?.setFilesPriority(ids, priority.toTorrentFilePriority())
    }

    fun createTree(rpcFiles: List<TorrentFile>) = scope.launch {
        val rootNode = Node.createRootNode()
        val files = mutableListOf<Node>()

        for ((fileIndex, rpcFile: TorrentFile) in rpcFiles.withIndex()) {
            var currentNode = rootNode

            val path: StringsVector = rpcFile.path
            val lastPartIndex = (path.size - 1)

            for ((partIndex, part: String) in path.withIndex()) {
                if (partIndex == lastPartIndex) {
                    val node = currentNode.addFile(fileIndex,
                                                   part,
                                                   rpcFile.size,
                                                   rpcFile.completedSize,
                                                   Item.WantedState.fromBoolean(rpcFile.wanted),
                                                   fromTorrentFilePriority(rpcFile.priority))
                    files.add(node)
                } else {
                    var childDirectoryNode = currentNode.getChildByItemNameOrNull(part)
                    if (childDirectoryNode == null) {
                        childDirectoryNode = currentNode.addDirectory(part)
                    }
                    currentNode = childDirectoryNode
                }
            }

            path.delete()
        }

        rootNode.initiallyCalculateFromChildrenRecursively()

        this@RpcTorrentFilesTree.files = files
        withContext(Dispatchers.Main) {
            model.treeCreated(rootNode)
        }
    }

    fun updateTree(changedFiles: List<TorrentFile>) = scope.launch {
        if (changedFiles.isEmpty()) return@launch

        val recalculateNodes = LinkedHashSet<Node>()

        for (rpcFile in changedFiles) {
            if (!isActive) return@launch

            val node = files[rpcFile.id]
            node.item = node.item.updatedFrom(rpcFile)

            var n = rootNode
            for (index in node.path) {
                recalculateNodes.add(n)
                n = n.children[index]
            }
        }

        var updateItems = false

        for (node in recalculateNodes.reversed()) {
            node.recalculateFromChildren()
            if (node === currentNode) {
                updateItems = true
            }
        }

        if (updateItems) {
            updateItemsWithSorting()
        }
    }
}