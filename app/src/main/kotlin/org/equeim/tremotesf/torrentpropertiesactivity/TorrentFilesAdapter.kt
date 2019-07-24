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

import java.text.DecimalFormat

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View

import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView

import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.createTextFieldDialog

import kotlinx.android.synthetic.main.text_field_dialog.*
import kotlinx.android.synthetic.main.torrent_file_list_item.view.*


class TorrentFilesAdapter(private val activity: TorrentPropertiesActivity,
                          rootDirectory: Directory) : BaseTorrentFilesAdapter(rootDirectory) {
    init {
        initSelector(activity, ActionModeCallback())
    }

    private val torrent: Torrent?
        get() {
            return activity.torrent
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.torrent_file_list_item,
                                                                   parent,
                                                                   false)
            Utils.setProgressBarColor(view.progress_bar)
            return ItemHolder(this, selector, view)
        }
        return super.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        if (holder.itemViewType == TYPE_ITEM) {
            holder as ItemHolder
            val item = holder.item
            holder.progressBar.progress = (item.progress * 100).toInt()
            holder.progressTextView.text = activity.getString(R.string.completed_string,
                                                              Utils.formatByteSize(activity,
                                                                                   item.completedSize),
                                                              Utils.formatByteSize(activity,
                                                                                   item.size),
                                                              DecimalFormat("0.#").format(item.progress * 100))
        }
    }

    override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
        Rpc.instance.setTorrentFilesWanted(torrent, ids, wanted)
    }

    override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
        Rpc.instance.setTorrentFilesPriority(torrent, ids, priority.toTorrentFilePriority())
    }

    fun treeUpdated() {
        val add = if (hasHeaderItem) 1 else 0
        for ((i, item) in currentItems.withIndex()) {
            if (item.changed) {
                notifyItemChanged(i + add)
            }
        }
        selector.actionMode?.invalidate()
    }

    fun reset() {
        val count = itemCount
        currentDirectory = rootDirectory
        currentItems.clear()
        notifyItemRangeRemoved(0, count)
    }

    fun fileRenamed(file: Item) {
        val index = currentItems.indexOf(file)
        if (index != -1) {
            if (hasHeaderItem) {
                notifyItemChanged(index + 1)
            } else {
                notifyItemChanged(index)
            }
        }
    }

    private class ItemHolder(adapter: BaseTorrentFilesAdapter,
                             selector: Selector<Item, Int>,
                             itemView: View) : BaseItemHolder(adapter, selector, itemView) {
        val progressBar = itemView.progress_bar!!
        val progressTextView = itemView.progress_text_view!!
    }

    private inner class ActionModeCallback : BaseActionModeCallback() {
        private var renameItem: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onCreateActionMode(mode, menu)
            if (Rpc.instance.serverSettings.canRenameFiles()) {
                mode.menuInflater.inflate(R.menu.torrent_files_context_menu, menu)
                renameItem = menu.findItem(R.id.rename)
            }
            mode.menuInflater.inflate(R.menu.select_all_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            super.onPrepareActionMode(mode, menu)
            renameItem?.isEnabled = (selector.selectedCount == 1)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            if (item == renameItem) {
                val file = selector.selectedItems.first()

                val pathParts = mutableListOf<String>()
                var directory = file
                while (directory != rootDirectory) {
                    pathParts.add(0, directory.name)
                    directory = directory.parentDirectory!!
                }

                torrent?.let { torrent ->
                    RenameDialogFragment.create(torrent.id(), pathParts.joinToString("/"), file.name)
                            .show(activity.supportFragmentManager, RenameDialogFragment.TAG)
                }

                return true
            }

            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            super.onDestroyActionMode(mode)
            renameItem = null
        }
    }

    class RenameDialogFragment : DialogFragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.torrentpropertiesactivity.TorrentFilesAdapter.RenameDialogFragment"
            private const val TORRENT_ID = "torrentId"
            private const val FILE_PATH = "filePath"
            private const val FILE_NAME = "fileName"

            fun create(torrentId: Int, filePath: String, fileName: String): RenameDialogFragment {
                val fragment = RenameDialogFragment()
                fragment.arguments = bundleOf(TORRENT_ID to torrentId,
                                              FILE_PATH to filePath,
                                              FILE_NAME to fileName)
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val fileName = arguments!!.getString(FILE_NAME)
            return createTextFieldDialog(requireContext(),
                                         null,
                                         null,
                                         null,
                                         getString(R.string.file_name),
                                         InputType.TYPE_TEXT_VARIATION_URI,
                                         fileName,
                                         null) {
                val path = arguments!!.getString(FILE_PATH)
                val newName = dialog!!.text_field.text.toString()
                Rpc.instance.renameTorrentFile(arguments!!.getInt(TORRENT_ID), path, newName)
                (activity as? Selector.ActionModeActivity)?.actionMode?.finish()
            }
        }
    }
}