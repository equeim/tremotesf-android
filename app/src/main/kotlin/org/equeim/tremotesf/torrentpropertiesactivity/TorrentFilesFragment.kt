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

package org.equeim.tremotesf.torrentpropertiesactivity

import java.lang.ref.WeakReference

import android.os.AsyncTask
import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.libtremotesf.TorrentFilesVector
import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.TorrentData

import org.equeim.tremotesf.setFilesEnabled

import kotlinx.android.synthetic.main.torrent_files_fragment.*


private fun updateFile(file: BaseTorrentFilesAdapter.File,
                       rpcFile: TorrentFile) {
    file.changed = rpcFile.changed
    if (file.changed) {
        file.size = rpcFile.size
        file.completedSize = rpcFile.completedSize
        file.setWanted(rpcFile.wanted)
        file.priority = BaseTorrentFilesAdapter.Item.Priority.fromTorrentFilePriority(rpcFile.priority)
    }
}

class TorrentFilesFragment : Fragment(R.layout.torrent_files_fragment) {
    private var instanceState: Bundle? = null

    private val torrentPropertiesFragment: TorrentPropertiesFragment
        get() = requireFragmentManager().findFragmentById(R.id.torrent_properties_fragment) as TorrentPropertiesFragment

    var torrent: TorrentData? = null
        private set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    value.torrent.setFilesEnabled(true)
                    Rpc.gotTorrentFilesListener = gotTorrentFilesListener
                    Rpc.torrentFileRenamedListener = fileRenamedListener
                }
            }
        }

    private val gotTorrentFilesListener = { torrentId: Int ->
        if (torrentId == torrent?.id) {
            update()
        }
    }
    private val fileRenamedListener = { torrentId: Int, filePath: String, newName: String ->
        if (torrentId == torrent?.id) {
            fileRenamed(filePath, newName)
        }
    }

    private var rootDirectory = BaseTorrentFilesAdapter.Directory()
    private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

    private var treeCreated = false

    private var creatingTree = false
    private var resetAfterCreate = false

    var adapter: TorrentFilesAdapter? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        update()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TorrentFilesAdapter(this, rootDirectory)
        this.adapter = adapter

        files_view.adapter = adapter
        files_view.layoutManager = LinearLayoutManager(requireContext())
        files_view.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        files_view.itemAnimator = null

        if (treeCreated) {
            adapter.restoreInstanceState(if (instanceState == null) savedInstanceState else instanceState)
        }

        adapter.isAtRootDirectoryListener = torrentPropertiesFragment::setBackPressedCallbackEnabledState

        updateProgressBar()
        updatePlaceholder()
    }

    override fun onStart() {
        super.onStart()
        update()
        Rpc.gotTorrentFilesListener = gotTorrentFilesListener
        Rpc.torrentFileRenamedListener = fileRenamedListener
    }

    override fun onStop() {
        super.onStop()
        Rpc.gotTorrentFilesListener = null
        Rpc.torrentFileRenamedListener = null
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        resetTree()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        instanceState = outState
        adapter?.saveInstanceState(outState)
    }

    fun update() {
        resetAfterCreate = false

        val newTorrent = torrentPropertiesFragment.torrent

        if (newTorrent == null) {
            if (torrent != null) {
                torrent = null
                if (treeCreated || creatingTree) {
                    resetTree()
                }
            }
            return
        }

        torrent = newTorrent
        if (newTorrent.torrent.isFilesLoaded) {
            val rpcFiles: TorrentFilesVector = newTorrent.torrent.files()
            if (rpcFiles.isEmpty()) {
                if (treeCreated || creatingTree) {
                    resetTree()
                }
            } else {
                if (treeCreated) {
                    if (newTorrent.torrent.isFilesChanged) {
                        updateTree(rpcFiles)
                    }
                } else if (!creatingTree) {
                    beginCreatingTree(rpcFiles)
                }
            }
        } else if (treeCreated || creatingTree) {
            resetTree()
        }
    }

    private fun fileRenamed(path: String, newName: String) {
        if (!treeCreated || creatingTree) {
            return
        }

        val pathParts = path.split('/').filter(String::isNotEmpty)
        var item: BaseTorrentFilesAdapter.Item? = rootDirectory
        for (part in pathParts) {
            item = (item as BaseTorrentFilesAdapter.Directory).children.find { it.name == part }
            if (item == null) {
                break
            }
        }
        if (item != rootDirectory && item != null) {
            item.name = newName
            adapter?.fileRenamed(item)
        }
    }

    private fun resetTree() {
        if (creatingTree) {
            resetAfterCreate = true
        } else {
            doResetTree()
        }
    }

    private fun doResetTree() {
        rootDirectory.clearChildren()
        files.clear()
        treeCreated = false
        adapter?.reset()
        updatePlaceholder()
    }

    private fun beginCreatingTree(rpcFiles: TorrentFilesVector) {
        creatingTree = true
        updateProgressBar()
        updatePlaceholder()
        TreeCreationTask(WeakReference(this), rpcFiles).execute()
    }

    private fun endCreatingTree(rootDirectory: BaseTorrentFilesAdapter.Directory,
                                files: List<BaseTorrentFilesAdapter.File>) {
        creatingTree = false
        treeCreated = true
        updateProgressBar()

        if (resetAfterCreate) {
            doResetTree()
            return
        }

        this.rootDirectory = rootDirectory
        this.files.addAll(files)

        adapter?.restoreInstanceState(null, rootDirectory)
    }

    private fun updateTree(rpcFiles: TorrentFilesVector) {
        for ((file, rpcFile: TorrentFile) in files.zip(rpcFiles)) {
            updateFile(file, rpcFile)
        }
        adapter?.treeUpdated()
    }

    private fun updatePlaceholder() {
        val torrent = this.torrent ?: return
        placeholder?.visibility = if (torrent.torrent.isFilesLoaded && !creatingTree && adapter?.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateProgressBar() {
        val torrent = this.torrent ?: return
        progress_bar?.visibility = if (!torrent.torrent.isFilesLoaded || creatingTree) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private class TreeCreationTask(private val fragment: WeakReference<TorrentFilesFragment>,
                                   private val rpcFiles: TorrentFilesVector) : AsyncTask<Any, Any, Any?>() {

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
            return null
        }

        override fun onPostExecute(result: Any?) {
            fragment.get()?.endCreatingTree(rootDirectory, files)
        }
    }
}