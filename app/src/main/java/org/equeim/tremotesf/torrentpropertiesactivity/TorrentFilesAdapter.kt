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

import java.text.DecimalFormat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View

import android.widget.ProgressBar
import android.widget.TextView

import android.support.v7.widget.RecyclerView

import org.equeim.tremotesf.BaseTorrentFilesAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Selector
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.utils.Utils


private fun idsFromItems(items: List<BaseTorrentFilesAdapter.Item>): List<Int> {
    val ids = mutableListOf<Int>()
    for (item in items) {
        if (item is BaseTorrentFilesAdapter.Directory) {
            ids.addAll(item.childrenIds)
        } else {
            ids.add((item as BaseTorrentFilesAdapter.File).id)
        }
    }
    return ids
}

class TorrentFilesAdapter(private val activity: TorrentPropertiesActivity,
                          rootDirectory: Directory) : BaseTorrentFilesAdapter(activity,
                                                                              rootDirectory) {
    private val torrent: Torrent?
        get() {
            return activity.torrent
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.torrent_file_list_item,
                                                                   parent,
                                                                   false)
            Utils.setProgressBarAccentColor(view.findViewById(R.id.progress_bar) as ProgressBar)
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

    override fun setSelectedItemsWanted(wanted: Boolean) {
        super.setSelectedItemsWanted(wanted)
        torrent!!.setFilesWanted(idsFromItems(selector.selectedItems), wanted)
    }

    override fun setSelectedItemsPriority(priority: Item.Priority) {
        super.setSelectedItemsPriority(priority)
        torrent!!.setFilesPriority(idsFromItems(selector.selectedItems), priority)
    }

    fun treeUpdated() {
        for ((i, item) in currentItems.withIndex()) {
            if (item.changed) {
                notifyItemChanged(i)
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
        val progressBar = itemView.findViewById(R.id.progress_bar) as ProgressBar
        val progressTextView = itemView.findViewById(R.id.progress_text_view) as TextView
    }
}