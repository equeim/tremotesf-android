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

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.tremotesf.data.TorrentFilesTree
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentViewModel.Companion.hasTorrent
import org.equeim.tremotesf.utils.Logger
import java.util.LinkedHashSet

class TorrentFilesFragmentViewModel(
    val torrent: StateFlow<Torrent?>,
    private val savedStateHandle: SavedStateHandle
) : ViewModel(), Logger {
    enum class State {
        None,
        Loading,
        CreatingTree,
        TreeCreated
    }

    private val _state = MutableStateFlow(State.None)
    val state: StateFlow<State> by ::_state

    val filesTree = RpcTorrentFilesTree(this, viewModelScope)

    init {
        info("constructor called")

        torrent.hasTorrent().onEach {
            if (it) {
                info("Torrent appeared, setting filesEnabled to true")
                torrent.value?.run {
                    filesEnabled = true
                    _state.value = State.Loading
                }
            } else {
                info("Torrent disappeared, resetting")
                reset()
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch { Rpc.torrentFilesUpdatedEvents.collect(::onTorrentFilesUpdated) }
        viewModelScope.launch {
            Rpc.torrentFileRenamedEvents.collect { (torrentId, filePath, newName) ->
                if (torrentId == torrent.value?.id) {
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

    fun treeCreated(rootNode: TorrentFilesTree.Node, files: List<TorrentFilesTree.Node>) {
        filesTree.init(rootNode, files, savedStateHandle)
        _state.value = State.TreeCreated
    }

    override fun onCleared() {
        info("onCleared() called")
        destroy()
    }

    fun destroy() {
        info("destroy() called")
        viewModelScope.cancel()
        reset()
        filesTree.dispatcher.close()
    }

    private fun reset() {
        info("reset() called")
        torrent.value?.filesEnabled = false
        if (state.value != State.None) {
            filesTree.reset()
            _state.value = State.None
        }
    }

    private fun onTorrentFilesUpdated(data: Rpc.TorrentFilesUpdatedData) {
        val (torrentId, changedFiles) = data
        if (torrentId == torrent.value?.id) {
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
                else -> {
                }
            }
        }
    }
}

class RpcTorrentFilesTree(
    private val model: TorrentFilesFragmentViewModel,
    parentScope: CoroutineScope
) : TorrentFilesTree(parentScope) {
    companion object {
        private fun Item.updatedFrom(rpcFile: TorrentFile): Item {
            return copy(
                name = name,
                completedSize = rpcFile.completedSize,
                wantedState = Item.WantedState.fromBoolean(rpcFile.wanted),
                priority = fromTorrentFilePriority(rpcFile.priority)
            )
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

    private var files: List<Node> = emptyList()

    val isEmpty: Boolean
        get() = files.isEmpty()

    override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
        model.torrent.value?.setFilesWanted(ids, wanted)
    }

    override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
        model.torrent.value?.setFilesPriority(ids, priority.toTorrentFilePriority())
    }

    @MainThread
    fun init(
        rootNode: Node,
        files: List<Node>,
        savedStateHandle: SavedStateHandle
    ) {
        init(rootNode, savedStateHandle)
        this.files = files
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
                    val node = currentNode.addFile(
                        fileIndex,
                        part,
                        rpcFile.size,
                        rpcFile.completedSize,
                        Item.WantedState.fromBoolean(rpcFile.wanted),
                        fromTorrentFilePriority(rpcFile.priority)
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

            path.delete()
        }

        rootNode.initiallyCalculateFromChildrenRecursively()

        withContext(Dispatchers.Main) {
            model.treeCreated(rootNode, files)
        }
    }

    fun updateTree(changedFiles: List<TorrentFile>) = scope.launch {
        if (changedFiles.isEmpty()) return@launch

        val files = this@RpcTorrentFilesTree.files
        val recalculateNodes = LinkedHashSet<Node>()

        for (rpcFile in changedFiles) {
            if (!isActive) return@launch

            val node = files.getOrNull(rpcFile.id) ?: continue
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

    @MainThread
    override fun reset() {
        super.reset()
        files = emptyList()
    }
}
