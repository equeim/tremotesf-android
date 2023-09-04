// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.hasSubscribersDebounced
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.torrentfile.buildTorrentFilesTree
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.TorrentFiles
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.getTorrentFiles
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.setTorrentFilesPriority
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.setTorrentFilesWanted
import timber.log.Timber

class TorrentFilesFragmentViewModel(
    val torrentHashString: String,
    private val savedStateHandle: SavedStateHandle,
    private val torrentFileRenamedEvents: Flow<TorrentPropertiesFragmentViewModel.TorrentFileRenamed>,
) : ViewModel() {
    sealed interface State {
        object Loading : State
        object CreatingTree : State
        object TreeCreated : State

        @JvmInline
        value class Error(val error: RpcRequestError) : State
        object TorrentNotFound : State
    }

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Loading)
    val state: StateFlow<State> by ::_state

    val filesTree = RpcTorrentFilesTree(this, viewModelScope)

    init {
        viewModelScope.launch {
            _state
                .hasSubscribersDebounced()
                .collectLatest { hasSubscribers ->
                    if (hasSubscribers) {
                        val performRequest = GlobalRpcClient.performPeriodicRequest { getTorrentFiles(torrentHashString) }
                        if (_state.value is State.TreeCreated) {
                            performRequest.dropWhile { it is RpcRequestState.Loading }
                        } else {
                            performRequest
                        }.collect(::onTorrentFilesUpdated)
                    }
                }
        }

        viewModelScope.launch {
            torrentFileRenamedEvents.collect { (filePath, newName) ->
                filesTree.renameFile(filePath, newName)
            }
        }
    }

    private fun createTree(rpcFiles: TorrentFiles): State {
        return if (rpcFiles.files.isEmpty()) {
            Timber.d("Files are empty, creating tree immediately")
            State.TreeCreated
        } else {
            Timber.d("Start creating tree")
            filesTree.createTree(rpcFiles)
            State.CreatingTree
        }
    }

    fun treeCreated(rootNode: TorrentFilesTree.DirectoryNode, files: List<TorrentFilesTree.FileNode>) {
        filesTree.init(rootNode, files, savedStateHandle)
        _state.value = State.TreeCreated
    }

    override fun onCleared() {
        Timber.i("onCleared() called")
        destroy()
    }

    fun destroy() {
        Timber.i("destroy() called")
        viewModelScope.cancel()
        filesTree.destroy()
    }

    private fun onTorrentFilesUpdated(requestState: RpcRequestState<TorrentFiles?>) {
        Timber.d("onTorrentFilesUpdated() called with: requestState = ${requestState::class.simpleName}")
        if (!(requestState is RpcRequestState.Loaded && _state.value is State.TreeCreated)) {
            filesTree.reset()
        }
        _state.value = when (requestState) {
            is RpcRequestState.Loading -> {
                State.Loading
            }

            is RpcRequestState.Error -> {
                State.Error(requestState.error)
            }

            is RpcRequestState.Loaded -> {
                val rpcFiles = requestState.response
                if (rpcFiles != null) {
                    Timber.d("Current state is ${state.value::class.simpleName}, filesTree.isEmpty = ${filesTree.isEmpty}")
                    when (state.value) {
                        is State.TreeCreated -> {
                            if (filesTree.isEmpty && rpcFiles.files.isNotEmpty()) {
                                // Torrent's metadata was downloaded
                                Timber.d("Creating tree")
                                createTree(rpcFiles)
                            } else {
                                Timber.d("Updating tree")
                                filesTree.updateTree(rpcFiles)
                                State.TreeCreated
                            }
                        }

                        is State.CreatingTree -> {
                            Timber.d("Already creating tree")
                            State.CreatingTree
                        }

                        else -> {
                            Timber.d("Creating tree")
                            createTree(rpcFiles)
                            State.CreatingTree
                        }
                    }
                } else {
                    State.TorrentNotFound
                }
            }
        }
    }
}

class RpcTorrentFilesTree(
    private val model: TorrentFilesFragmentViewModel,
    parentScope: CoroutineScope,
) : TorrentFilesTree(parentScope) {
    companion object {
        private fun Item.updatedFromIfNeeded(file: TorrentFiles.File, fileStats: TorrentFiles.FileStats): Item? {
            val newName = file.name
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

    override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_files_wanted_error) {
            GlobalRpcClient.setTorrentFilesWanted(
                model.torrentHashString,
                ids.asList(),
                wanted
            )
        }
    }

    override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_files_priority_error) {
            GlobalRpcClient.setTorrentFilesPriority(
                model.torrentHashString,
                ids.asList(),
                priority.toTorrentFilePriority()
            )
        }
    }

    @MainThread
    fun init(
        rootNode: DirectoryNode,
        files: List<FileNode>,
        savedStateHandle: SavedStateHandle,
    ) {
        Timber.d("Tree created, files.size = ${files.size}")
        init(rootNode, savedStateHandle)
        this.files = files
    }

    fun createTree(rpcFiles: TorrentFiles) = scope.launch {
        try {
            val (rootNode, files) = buildTorrentFilesTree {
                for (index in rpcFiles.files.indices) {
                    ensureActive()

                    val file = rpcFiles.files[index]
                    val fileStats = rpcFiles.fileStats[index]

                    val path = file.name.splitToSequence('/').filter { it.isNotEmpty() }.toList()
                    addFile(
                        fileId = index,
                        path = path,
                        size = file.size.bytes,
                        completedSize = fileStats.completedSize.bytes,
                        wantedState = Item.WantedState.fromBoolean(fileStats.wanted),
                        priority = fileStats.priority.toTreeItemPriority()
                    )
                }
            }
            withContext(Dispatchers.Main) {
                model.treeCreated(rootNode, files)
            }
        } catch (e: IllegalArgumentException) {
            if (BuildConfig.DEBUG) {
                throw e
            } else {
                Timber.e(e, "Failed to build torrent files tree")
            }
        }
    }

    fun updateTree(rpcFiles: TorrentFiles) {
        scope.launch {
            val files = this@RpcTorrentFilesTree.files
            if (files.size != rpcFiles.files.size) {
                Timber.e("New files have different count")
                return@launch
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
    }

    @MainThread
    override fun reset() {
        super.reset()
        files = emptyList()
    }
}
