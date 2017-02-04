/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.app.Fragment
import android.os.AsyncTask
import android.os.Bundle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

import com.google.gson.JsonArray
import com.google.gson.JsonObject

import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Torrent

import kotlinx.android.synthetic.main.torrent_files_fragment.*


private fun updateFile(file: BaseTorrentFilesAdapter.File,
                       fileJson: JsonObject,
                       fileStatsJson: JsonObject) {
    file.changed = false
    file.size = fileJson["length"].asLong
    file.completedSize = fileJson["bytesCompleted"].asLong
    file.setWanted(fileStatsJson["wanted"].asBoolean)
    file.priority = when (fileStatsJson["priority"].asInt) {
        -1 -> BaseTorrentFilesAdapter.Item.Priority.Low
        0 -> BaseTorrentFilesAdapter.Item.Priority.Normal
        1 -> BaseTorrentFilesAdapter.Item.Priority.High
        else -> BaseTorrentFilesAdapter.Item.Priority.Normal
    }
}

class TorrentFilesFragment : Fragment() {
    private var instanceState: Bundle? = null

    private val activity: TorrentPropertiesActivity
        get() {
            return getActivity() as TorrentPropertiesActivity
        }

    private var torrent: Torrent? = null
        set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    value.filesUpdateEnabled = true
                    value.filesLoadedListener = filesLoadedListener
                    value.fileRenamedListener = fileRenamedListener
                }
            }
        }

    private val filesLoadedListener = { update() }
    private val fileRenamedListener = { filePath: String, newName: String ->
        fileRenamed(filePath, newName)
    }

    val rootDirectory = BaseTorrentFilesAdapter.Directory()
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
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.torrent_files_fragment, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TorrentFilesAdapter(activity, rootDirectory)

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
        torrent?.filesLoadedListener = filesLoadedListener
    }

    override fun onStop() {
        super.onStop()
        torrent?.filesLoadedListener = null
        torrent?.fileRenamedListener = null
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

        val newTorrent = activity.torrent

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

        val fileJsons = newTorrent.fileJsons
        val fileStatsJsons = newTorrent.fileStatsJsons

        if ((fileJsons?.size() ?: 0) == 0) {
            if (treeCreated || creatingTree) {
                resetTree()
            }
        } else {
            if (treeCreated || creatingTree) {
                updateTree()
            } else {
                createTree(fileJsons!!, fileStatsJsons!!)
            }
        }
    }

    fun fileRenamed(path: String, newName: String) {
        if (!treeCreated || creatingTree) {
            return
        }

        val pathParts = path.split('/').filter(String::isNotEmpty)
        var item: BaseTorrentFilesAdapter.Item? = rootDirectory
        for (part in pathParts) {
            item = (item as BaseTorrentFilesAdapter.Directory).children.find { item -> item.name == part }
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
        rootDirectory.children.clear()
        files.clear()
        treeCreated = false
        adapter?.reset()
        updatePlaceholder()
    }

    private fun createTree(fileJsons: JsonArray, fileStatsJsons: JsonArray) {
        creatingTree = true
        updateProgressBar()
        updatePlaceholder()

        object : AsyncTask<Any, Any, Any>() {
            override fun doInBackground(vararg params: Any?): Any? {
                for (fileIndex in 0..(fileJsons.size() - 1)) {
                    val fileJson = fileJsons[fileIndex].asJsonObject
                    val fileStatsJson = fileStatsJsons[fileIndex].asJsonObject

                    var currentDirectory = rootDirectory

                    val filePath = fileJson["name"].asString
                    val parts = filePath.split('/').filter(String::isNotEmpty)
                    for ((partIndex, part) in parts.withIndex()) {
                        if (partIndex == parts.lastIndex) {
                            val file = BaseTorrentFilesAdapter.File(currentDirectory.children.size,
                                                                    currentDirectory,
                                                                    part,
                                                                    fileIndex)
                            updateFile(file, fileJson, fileStatsJson)
                            currentDirectory.children.add(file)
                            files.add(file)
                        } else {
                            var childDirectory = currentDirectory.children.find { item ->
                                (item is BaseTorrentFilesAdapter.Directory && item.name == part)
                            }
                            if (childDirectory == null) {
                                childDirectory = BaseTorrentFilesAdapter.Directory(currentDirectory.children.size,
                                                                                   currentDirectory,
                                                                                   part)
                                currentDirectory.children.add(childDirectory)

                            }
                            currentDirectory = childDirectory as BaseTorrentFilesAdapter.Directory
                        }
                    }
                }
                return null
            }

            override fun onPostExecute(result: Any?) {
                creatingTree = false
                treeCreated = true
                updateProgressBar()
                if (resetAfterCreate) {
                    doResetTree()
                    return
                }
                if (updateAfterCreate) {
                    doUpdateTree()
                }
                adapter?.restoreInstanceState(null)
            }
        }.execute()
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
        val fileJsons = torrent!!.fileJsons!!
        val fileStatsJsons = torrent!!.fileStatsJsons!!
        for ((i, file) in files.withIndex()) {
            updateFile(file, fileJsons[i].asJsonObject, fileStatsJsons[i].asJsonObject)
        }
        adapter?.treeUpdated()
    }

    private fun updatePlaceholder() {
        if (torrent == null) {
            return
        }
        placeholder?.visibility = if (torrent!!.fileJsons != null && !creatingTree && adapter!!.itemCount == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun updateProgressBar() {
        if (torrent == null) {
            return
        }
        progress_bar?.visibility = if (torrent!!.fileJsons == null || creatingTree) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}