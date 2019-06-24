/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.Torrent
import org.equeim.libtremotesf.TorrentFile
import org.equeim.libtremotesf.TorrentFilesVector
import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc

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

class TorrentFilesFragment : Fragment() {
    private var instanceState: Bundle? = null

    private val activity: TorrentPropertiesActivity?
        get() {
            return getActivity() as? TorrentPropertiesActivity
        }

    private var torrent: Torrent? = null
        set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    Rpc.instance.setTorrentFilesEnabled(value, true)
                    Rpc.instance.gotTorrentFilesListener = gotTorrentFilesListener
                    Rpc.instance.torrentFileRenamedListener = fileRenamedListener
                }
            }
        }

    private val gotTorrentFilesListener = { torrentId: Int ->
        if (torrentId == torrent?.id()) {
            update()
        }
    }
    private val fileRenamedListener = { torrentId: Int, filePath: String, newName: String ->
        if (torrentId == torrent?.id()) {
            fileRenamed(filePath, newName)
        }
    }

    private var rootDirectory = BaseTorrentFilesAdapter.Directory()
    private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

    private var treeCreated = false

    private var creatingTree = false
    private var updateAfterCreate = false
    private var resetAfterCreate = false

    private var adapter: TorrentFilesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        update()
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.torrent_files_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TorrentFilesAdapter(activity!!, rootDirectory)

        files_view.adapter = adapter
        files_view.layoutManager = LinearLayoutManager(activity)
        files_view.addItemDecoration(DividerItemDecoration(activity,
                DividerItemDecoration.VERTICAL))
        files_view.itemAnimator = null

        if (treeCreated) {
            adapter!!.restoreInstanceState(if (instanceState == null) savedInstanceState else instanceState)
        }

        updateProgressBar()
        updatePlaceholder()
    }

    override fun onStart() {
        super.onStart()
        update()
        Rpc.instance.gotTorrentFilesListener = gotTorrentFilesListener
        Rpc.instance.torrentFileRenamedListener = fileRenamedListener
    }

    override fun onStop() {
        super.onStop()
        Rpc.instance.gotTorrentFilesListener = null
        Rpc.instance.torrentFileRenamedListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTree()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        instanceState = outState
        adapter?.saveInstanceState(outState)
    }

    fun onBackPressed(): Boolean {
        return adapter?.navigateUp() ?: false
    }

    fun update() {
        updateAfterCreate = false
        resetAfterCreate = false

        val newTorrent = activity?.torrent

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

        val filesVector: TorrentFilesVector = newTorrent.files()

        if (filesVector.isEmpty()) {
            if (treeCreated || creatingTree) {
                resetTree()
            }
        } else {
            if (treeCreated || creatingTree) {
                updateTree()
            } else {
                beginCreatingTree(filesVector)
            }
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
            updateAfterCreate = false
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

    private fun beginCreatingTree(filesVector: TorrentFilesVector) {
        creatingTree = true
        updateProgressBar()
        updatePlaceholder()
        TreeCreationTask(WeakReference(this), filesVector).execute()
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

        if (updateAfterCreate) {
            doUpdateTree()
        }

        adapter?.restoreInstanceState(null, rootDirectory)
    }

    private fun updateTree() {
        if (creatingTree) {
            updateAfterCreate = true
            resetAfterCreate = false
        } else {
            doUpdateTree()
        }
    }

    private fun doUpdateTree() {
        val rpcFiles = torrent!!.files()
        for ((i, file) in files.withIndex()) {
            updateFile(file, rpcFiles[i])
        }
        adapter?.treeUpdated()
    }

    private fun updatePlaceholder() {
        if (torrent == null) {
            return
        }
        placeholder?.visibility = if (torrent!!.isFilesLoaded && !creatingTree && adapter!!.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateProgressBar() {
        if (torrent == null) {
            return
        }
        progress_bar?.visibility = if (!torrent!!.isFilesLoaded || creatingTree) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private class TreeCreationTask(private val fragment: WeakReference<TorrentFilesFragment>,
                                   private val filesVector: TorrentFilesVector) : AsyncTask<Any, Any, Any?>() {

        private val rootDirectory = BaseTorrentFilesAdapter.Directory()
        private val files = mutableListOf<BaseTorrentFilesAdapter.File>()

        override fun doInBackground(vararg params: Any?): Any? {
            for ((fileIndex, rpcFile: TorrentFile) in filesVector.withIndex()) {
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