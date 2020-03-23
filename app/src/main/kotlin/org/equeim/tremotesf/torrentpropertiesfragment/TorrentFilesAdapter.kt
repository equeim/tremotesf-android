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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.Utils
import org.equeim.tremotesf.utils.safeNavigate

import kotlinx.android.synthetic.main.torrent_file_list_item.view.*


class TorrentFilesAdapter(private val fragment: TorrentFilesFragment,
                          rootDirectory: Directory) : BaseTorrentFilesAdapter(rootDirectory, fragment.requireActivity() as AppCompatActivity) {
    private val torrent: Torrent?
        get() {
            return fragment.torrent
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
            val context = fragment.requireContext()
            holder.progressTextView.text = context.getString(R.string.completed_string,
                                                              Utils.formatByteSize(context,
                                                                                   item.completedSize),
                                                              Utils.formatByteSize(context,
                                                                                   item.size),
                                                              DecimalFormats.generic.format(item.progress * 100))
        }
    }

    override fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {
        torrent?.setFilesWanted(ids, wanted)
    }

    override fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {
        torrent?.setFilesPriority(ids, priority.toTorrentFilePriority())
    }

    override fun onNavigateToRenameDialog(args: Bundle) {
        fragment.findNavController().safeNavigate(R.id.action_torrentPropertiesFragment_to_torrentRenameDialogFragment, args)
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

    private class ItemHolder(adapter: BaseTorrentFilesAdapter,
                             selector: Selector<Item, Int>,
                             itemView: View) : BaseItemHolder(adapter, selector, itemView) {
        val progressBar = itemView.progress_bar!!
        val progressTextView = itemView.progress_text_view!!
    }
}