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

package org.equeim.tremotesf.torrentpropertiesfragment

import java.lang.ref.WeakReference

import android.os.AsyncTask
import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.Torrent
import org.equeim.libtremotesf.TorrentFile
import org.equeim.libtremotesf.TorrentFilesVector
import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.TorrentData
import org.equeim.tremotesf.utils.NonNullMutableLiveData

import org.equeim.tremotesf.setFilesEnabled

import kotlinx.android.synthetic.main.torrent_files_fragment.*


class TorrentFilesFragment : Fragment(R.layout.torrent_files_fragment), TorrentPropertiesFragment.PagerFragment {
    private val torrentPropertiesFragment: TorrentPropertiesFragment?
        get() = parentFragment as TorrentPropertiesFragment?

    var torrent: TorrentData? = null
        private set(value) {
            if (value != field) {
                field = value
                value?.torrent?.setFilesEnabled(true)
                model.update(value?.torrent)
            } else if (value != null) {
                model.update(value.torrent)
            }
        }

    private var savedInstanceState: Bundle? = null

    val model: TreeModel by viewModels()

    var adapter: TorrentFilesAdapter? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.savedInstanceState = savedInstanceState
        update()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TorrentFilesAdapter(this, model.rootDirectory)
        this.adapter = adapter

        files_view.adapter = adapter
        files_view.layoutManager = LinearLayoutManager(requireContext())
        files_view.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        files_view.itemAnimator = null

        model.status.observe(viewLifecycleOwner) { status ->
            if (status == TreeModel.Status.Created) {
                if (adapter.rootDirectory != model.rootDirectory || savedInstanceState != null) {
                    adapter.restoreInstanceState(this.savedInstanceState, model.rootDirectory)
                    this.savedInstanceState = null
                } else {
                    adapter.treeUpdated()
                }
            } else if (status == TreeModel.Status.None) {
                adapter.reset()
            }

            updatePlaceholder()
            updateProgressBar()
        }

        Rpc.gotTorrentFilesEvent.observe(viewLifecycleOwner) { torrentId ->
            if (torrentId == torrent?.id) {
                update()
            }
        }
        Rpc.torrentFileRenamedEvent.observe(viewLifecycleOwner) { (torrentId, filePath, newName) ->
            if (torrentId == torrent?.id) {
                adapter.fileRenamed(model.renameFile(filePath, newName))
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
        torrentPropertiesFragment?.let {
            torrent = it.torrent
        }
    }

    private fun updatePlaceholder() {
        val torrent = this.torrent ?: return
        placeholder?.visibility = if (torrent.torrent.isFilesLoaded && model.status.value != TreeModel.Status.Creating && adapter?.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateProgressBar() {
        val torrent = this.torrent ?: return
        progress_bar?.visibility = if (!torrent.torrent.isFilesLoaded || model.status.value == TreeModel.Status.Creating) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    class TreeModel : ViewModel() {
        enum class Status {
            None,
            Creating,
            Created
        }

        private companion object {
            fun updateFile(file: BaseTorrentFilesAdapter.File,
                                   rpcFile: TorrentFile,
                                   force: Boolean = false) {
                file.changed = rpcFile.changed
                if (file.changed || force) {
                    file.size = rpcFile.size
                    file.completedSize = rpcFile.completedSize
                    file.setWanted(rpcFile.wanted)
                    file.priority = BaseTorrentFilesAdapter.Item.Priority.fromTorrentFilePriority(rpcFile.priority)
                }
            }
        }

        val status = NonNullMutableLiveData(Status.None)
        var rootDirectory = BaseTorrentFilesAdapter.Directory()
            private set
        var files: List<BaseTorrentFilesAdapter.File> = emptyList()
            private set

        fun update(torrent: Torrent?) {
            if (torrent == null) {
                resetTree()
                return
            }

            if (torrent.isFilesLoaded) {
                val rpcFiles: TorrentFilesVector = torrent.files()
                if (rpcFiles.isEmpty()) {
                    resetTree()
                } else {
                    if (status.value == Status.Created) {
                        if (torrent.isFilesChanged) {
                            updateTree(rpcFiles)
                        }
                    } else if (status.value != Status.Creating) {
                        createTree(rpcFiles)
                    }
                }
            } else {
                resetTree()
            }
        }

        private fun createTree(rpcFiles: TorrentFilesVector) {
            status.value = Status.Creating
            TreeCreationTask(this, rpcFiles).execute()
        }

        private fun updateTree(rpcFiles: TorrentFilesVector) {
            for ((file, rpcFile: TorrentFile) in files.zip(rpcFiles)) {
                updateFile(file, rpcFile)
            }
            status.value = Status.Created
        }

        private fun resetTree() {
            if (status.value != Status.None) {
                rootDirectory.clearChildren()
                files = emptyList()
                status.value = Status.None
            }
        }

        fun renameFile(path: String, newName: String): BaseTorrentFilesAdapter.Item? {
            if (status.value != Status.Created) {
                return null
            }

            val pathParts = path.split('/').filter(String::isNotEmpty)
            var item: BaseTorrentFilesAdapter.Item? = rootDirectory
            for (part in pathParts) {
                item = (item as BaseTorrentFilesAdapter.Directory).children.find { it.name == part }
                if (item == null) {
                    break
                }
            }
            if (item == rootDirectory) {
                item = null
            }

            item?.name = newName

            return item
        }

        private class TreeCreationTask(model: TreeModel,
                                       private val rpcFiles: TorrentFilesVector) : AsyncTask<Any, Any, Any?>() {
            private val model = WeakReference(model)
            private val rootDirectory = BaseTorrentFilesAdapter.Directory()
            private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

            override fun doInBackground(vararg params: Any?): Any? {
                for ((fileIndex, rpcFile: TorrentFile) in rpcFiles.withIndex()) {
                    var currentDirectory = rootDirectory

                    val path: StringsVector = rpcFile.path
                    val lastPartIndex = (path.size - 1)

                    for ((partIndex, part: String) in path.withIndex()) {
                        if (partIndex == lastPartIndex) {
                            val file = BaseTorrentFilesAdapter.File(currentDirectory.children.size,
                                                                    currentDirectory,
                                                                    part,
                                                                    fileIndex)
                            updateFile(file, rpcFile, true)
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
                return null
            }

            override fun onPostExecute(result: Any?) {
                model.get()?.let { model ->
                    if (model.status.value == TreeModel.Status.Creating) {
                        model.rootDirectory = rootDirectory
                        model.files = files
                        model.status.value = TreeModel.Status.Created
                    }
                }
            }
        }
    }
}
