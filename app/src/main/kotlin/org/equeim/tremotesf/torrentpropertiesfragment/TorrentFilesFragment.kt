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

package org.equeim.tremotesf.torrentpropertiesfragment

import kotlin.properties.Delegates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.databinding.TorrentFilesFragmentBinding
import org.equeim.tremotesf.utils.NonNullMutableLiveData
import org.equeim.tremotesf.utils.viewBinding


class TorrentFilesFragment : Fragment(R.layout.torrent_files_fragment), TorrentPropertiesFragment.PagerFragment {
    private val torrentPropertiesFragment: TorrentPropertiesFragment
        get() = requireParentFragment() as TorrentPropertiesFragment

    private var savedInstanceState: Bundle? = null

    private lateinit var model: TreeModel

    var torrent: Torrent? = null
        private set

    private val binding by viewBinding(TorrentFilesFragmentBinding::bind)
    var adapter: TorrentFilesAdapter? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState
        model = ViewModelProvider(torrentPropertiesFragment,
                                  TreeModelFactory(torrentPropertiesFragment.torrent))[TreeModel::class.java]
        torrent = model.torrent
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TorrentFilesAdapter(this, model.rootDirectory)
        this.adapter = adapter

        binding.filesView.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            itemAnimator = null
        }

        model.state.observe(viewLifecycleOwner) { state ->
            if (state == TreeModel.State.TreeCreated) {
                if (adapter.rootDirectory != model.rootDirectory || savedInstanceState != null) {
                    adapter.restoreInstanceState(this.savedInstanceState, model.rootDirectory)
                    this.savedInstanceState = null
                } else {
                    adapter.treeUpdated()
                }
            } else if (state == TreeModel.State.None) {
                adapter.reset()
            }

            updatePlaceholder(state)
            updateProgressBar(state)
        }

        Rpc.torrentFileRenamedEvent.observe(viewLifecycleOwner) { (torrentId, filePath, newName) ->
            if (torrentId == torrent?.id) {
                adapter.fileRenamed(filePath, newName)
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        adapter?.saveInstanceState(outState)
    }

    override fun update() {
        torrent = torrentPropertiesFragment.torrent
        model.torrent = torrent
    }

    override fun onNavigatedFrom() {
        model.state.removeObservers(viewLifecycleOwner)
        model.torrent = null
    }

    private fun updatePlaceholder(modelState: TreeModel.State) {
        binding.placeholder.visibility = if (modelState == TreeModel.State.TreeCreated && model.files.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateProgressBar(modelState: TreeModel.State) {
        binding.progressBar.visibility = when (modelState) {
            TreeModel.State.Loading,
            TreeModel.State.CreatingTree -> View.VISIBLE
            else -> View.GONE
        }
    }

    private class TreeModelFactory(private val torrent: Torrent?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == TreeModel::class.java) {
                @Suppress("UNCHECKED_CAST")
                return TreeModel(torrent) as T
            }
            throw IllegalArgumentException()
        }
    }

    private class TreeModel(torrent: Torrent?) : ViewModel() {
        enum class State {
            None,
            Loading,
            CreatingTree,
            TreeCreated
        }

        private companion object {
            fun updateFile(file: BaseTorrentFilesAdapter.File, rpcFile: TorrentFile) {
                file.completedSize = rpcFile.completedSize
                file.setWanted(rpcFile.wanted)
                file.priority = BaseTorrentFilesAdapter.Item.Priority.fromTorrentFilePriority(rpcFile.priority)
            }
        }

        var torrent by Delegates.observable<Torrent?>(null) { _, oldTorrent, torrent ->
            if (torrent == null) {
                oldTorrent?.filesEnabled = false
                reset()
                Rpc.torrentFilesUpdatedEvent.removeObserver(filesUpdatedObserver)
            } else {
                torrent.filesEnabled = true
                state.value = State.Loading
                Rpc.torrentFilesUpdatedEvent.observeForever(filesUpdatedObserver)
            }
        }

        var rootDirectory = BaseTorrentFilesAdapter.Directory()
            private set
        var files: List<BaseTorrentFilesAdapter.File> = emptyList()
            private set
        val state = NonNullMutableLiveData(State.None)

        private val filesUpdatedObserver = Observer(::onTorrentFilesUpdated)

        init {
            this.torrent = torrent
        }

        private fun createTree(rpcFiles: List<TorrentFile>) {
            if (rpcFiles.isEmpty()) {
                state.value = State.TreeCreated
            } else {
                state.value = State.CreatingTree
                viewModelScope.launch(Dispatchers.Default) {
                    doCreateTree(rpcFiles)
                }
            }
        }

        private suspend fun doCreateTree(rpcFiles: List<TorrentFile>) {
            val rootDirectory = BaseTorrentFilesAdapter.Directory()
            val files = mutableListOf<BaseTorrentFilesAdapter.File>()

            for ((fileIndex, rpcFile: TorrentFile) in rpcFiles.withIndex()) {
                var currentDirectory = rootDirectory

                val path: StringsVector = rpcFile.path
                val lastPartIndex = (path.size - 1)

                for ((partIndex, part: String) in path.withIndex()) {
                    if (partIndex == lastPartIndex) {
                        val file = BaseTorrentFilesAdapter.File(currentDirectory.children.size,
                                                                currentDirectory,
                                                                part,
                                                                rpcFile.size,
                                                                fileIndex)
                        updateFile(file, rpcFile)
                        currentDirectory.addChild(file)
                        files.add(file)
                    } else {
                        var childDirectory = currentDirectory.childrenMap[part]
                                as BaseTorrentFilesAdapter.Directory?
                        if (childDirectory == null) {
                            childDirectory = BaseTorrentFilesAdapter.Directory(currentDirectory.children.size,
                                                                               currentDirectory,
                                                                               part)
                            currentDirectory.addChild(childDirectory)

                        }
                        currentDirectory = childDirectory
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (state.value == State.CreatingTree) {
                    this@TreeModel.rootDirectory = rootDirectory
                    this@TreeModel.files = files
                    this@TreeModel.state.value = State.TreeCreated
                }
            }
        }

        private fun updateTree(changedFiles: List<TorrentFile>) {
            if (changedFiles.isNotEmpty()) {
                val changed = ArrayList<BaseTorrentFilesAdapter.File>(changedFiles.size)
                for (rpcFile in changedFiles) {
                    val file = files[rpcFile.id]
                    updateFile(file, rpcFile)
                    file.changed = true
                    changed.add(file)
                }
                state.value = state.value
                for (file in changed) {
                    file.changed = false
                }
            }
        }

        private fun reset() {
            if (state.value != State.None) {
                if (state.value == State.CreatingTree) {
                    viewModelScope.coroutineContext.cancelChildren()
                }
                rootDirectory.clearChildren()
                files = emptyList()
                state.value = State.None
            }
        }

        private fun onTorrentFilesUpdated(data: Rpc.TorrentFilesUpdatedData) {
            val (torrentId, changedFiles) = data
            if (torrentId == torrent?.id) {
                when (state.value) {
                    State.TreeCreated -> {
                        if (files.isEmpty() && changedFiles.isNotEmpty()) {
                            // Torrent's metadata was downloaded
                            createTree(changedFiles)
                        } else {
                            updateTree(changedFiles)
                        }
                    }
                    State.Loading -> createTree(changedFiles)
                    else -> {}
                }
            }
        }

        override fun onCleared() {
            torrent = null
        }
    }
}
