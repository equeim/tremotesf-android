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

package org.equeim.tremotesf.ui

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.utils.StateRestoringListAdapter
import org.equeim.tremotesf.ui.views.TristateCheckbox
import java.lang.ref.WeakReference


abstract class BaseTorrentFilesAdapter(
    private val filesTree: TorrentFilesTree,
    private val fragment: Fragment
) : StateRestoringListAdapter<TorrentFilesTree.Item?, RecyclerView.ViewHolder>(ItemCallback()) {
    protected companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    lateinit var selectionTracker: SelectionTracker<Int>
        private set

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        selectionTracker = SelectionTracker.createForIntKeys(
            this,
            true,
            fragment,
            { ActionModeCallback(this, it) },
            R.plurals.files_selected
        ) {
            getItem(it)?.nodePath?.last() ?: SelectionTracker.SELECTION_KEY_UNSELECTABLE_INT
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) == null) {
            TYPE_HEADER
        } else {
            TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.up_list_item,
                    parent,
                    false
                )
            )
        }
        throw IllegalArgumentException()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == TYPE_ITEM) {
            (holder as BaseItemHolder).update()
        }
    }

    fun update(items: List<TorrentFilesTree.Item?>) {
        submitList(items) {
            selectionTracker.commitAdapterUpdate()
        }
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    private fun setSelectedItemsWanted(wanted: Boolean) {
        val nodeIndexes =
            selectionTracker.getSelectedPositionsUnsorted().map { getItem(it)!!.nodePath.last() }
        filesTree.setItemsWanted(nodeIndexes, wanted)
    }

    private fun setSelectedItemsPriority(priority: TorrentFilesTree.Item.Priority) {
        val nodeIndexes =
            selectionTracker.getSelectedPositionsUnsorted().map { getItem(it)!!.nodePath.last() }
        filesTree.setItemsPriority(nodeIndexes, priority)
    }

    protected abstract fun navigateToRenameDialog(path: String, name: String)

    private class ItemCallback : DiffUtil.ItemCallback<TorrentFilesTree.Item?>() {
        override fun areItemsTheSame(
            oldItem: TorrentFilesTree.Item,
            newItem: TorrentFilesTree.Item
        ): Boolean {
            return oldItem === newItem || oldItem.nodePath.contentEquals(newItem.nodePath)
        }

        override fun areContentsTheSame(
            oldItem: TorrentFilesTree.Item,
            newItem: TorrentFilesTree.Item
        ): Boolean {
            return oldItem == newItem
        }
    }

    private inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                if (!selectionTracker.hasSelection) {
                    filesTree.navigateUp()
                }
            }
        }
    }

    protected abstract class BaseItemHolder(
        private val adapter: BaseTorrentFilesAdapter,
        selectionTracker: SelectionTracker<Int>,
        itemView: View
    ) : SelectionTracker.ViewHolder<Int>(
        selectionTracker,
        itemView
    ) {

        private val iconView: ImageView = itemView.findViewById(R.id.icon_view)
        @DrawableRes private var iconResId = 0
        private val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        private val checkBox: TristateCheckbox = itemView.findViewById(R.id.check_box)

        init {
            checkBox.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != -1) {
                    with(adapter) {
                        filesTree.setItemsWanted(
                            listOf(getItem(position)!!.nodePath.last()),
                            checkBox.isChecked
                        )
                    }
                }
            }
        }

        override fun onClick(view: View) {
            val position = bindingAdapterPosition
            if (position != -1) {
                with(adapter) {
                    filesTree.navigateDown(getItem(position)!!)
                }
            }
        }

        override fun update() {
            super.update()

            val item = adapter.getItem(bindingAdapterPosition)!!

            val resId = if (item.isDirectory) R.drawable.ic_folder_24dp else R.drawable.ic_insert_drive_file_24dp
            if (resId != iconResId) {
                iconView.setImageResource(resId)
                iconResId = resId
            }

            nameTextView.text = item.name

            checkBox.state = when (item.wantedState) {
                TorrentFilesTree.Item.WantedState.Wanted -> TristateCheckbox.State.Checked
                TorrentFilesTree.Item.WantedState.Unwanted -> TristateCheckbox.State.Unchecked
                TorrentFilesTree.Item.WantedState.Mixed -> TristateCheckbox.State.Indeterminate
            }
            checkBox.isEnabled = !selectionTracker.hasSelection
        }
    }

    private class ActionModeCallback(adapter: BaseTorrentFilesAdapter, selectionTracker: SelectionTracker<Int>) :
        SelectionTracker.ActionModeCallback<Int>(selectionTracker) {

        private val adapter = WeakReference(adapter)

        private var downloadItem: MenuItem? = null
        private var notDownloadItem: MenuItem? = null
        private var lowPriorityItem: MenuItem? = null
        private var normalPriorityItem: MenuItem? = null
        private var highPriorityItem: MenuItem? = null
        private var mixedPriorityItem: MenuItem? = null
        private var renameItem: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.torrent_files_context_menu, menu)

            downloadItem = menu.findItem(R.id.download)
            notDownloadItem = menu.findItem(R.id.not_download)
            lowPriorityItem = menu.findItem(R.id.low_priority)
            normalPriorityItem = menu.findItem(R.id.normal_priority)
            highPriorityItem = menu.findItem(R.id.high_priority)
            mixedPriorityItem = menu.findItem(R.id.mixed_priority)
            renameItem = menu.findItem(R.id.rename)

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val selectionTracker = this.selectionTracker
            if (selectionTracker?.hasSelection != true) {
                return super.onPrepareActionMode(mode, menu)
            }

            super.onPrepareActionMode(mode, menu)

            if (selectionTracker.selectedCount == 1) {
                val first = adapter.get()?.getItem(selectionTracker.getFirstSelectedPosition())!!
                val wanted = (first.wantedState == TorrentFilesTree.Item.WantedState.Wanted)
                downloadItem!!.isEnabled = !wanted
                notDownloadItem!!.isEnabled = wanted
                when (first.priority) {
                    TorrentFilesTree.Item.Priority.Low -> lowPriorityItem
                    TorrentFilesTree.Item.Priority.Normal -> normalPriorityItem
                    TorrentFilesTree.Item.Priority.High -> highPriorityItem
                    TorrentFilesTree.Item.Priority.Mixed -> mixedPriorityItem
                }!!.isChecked = true
                mixedPriorityItem!!.isVisible =
                    (first.priority == TorrentFilesTree.Item.Priority.Mixed)
                renameItem!!.isEnabled = true
            } else {
                downloadItem!!.isEnabled = true
                notDownloadItem!!.isEnabled = true
                mixedPriorityItem!!.isVisible = true
                mixedPriorityItem!!.isChecked = true
                renameItem!!.isEnabled = false
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            downloadItem = null
            notDownloadItem = null
            lowPriorityItem = null
            normalPriorityItem = null
            highPriorityItem = null
            mixedPriorityItem = null
            renameItem = null
            super.onDestroyActionMode(mode)
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (super.onActionItemClicked(mode, item)) {
                return true
            }

            return adapter.get()?.run {
                var ret = true
                when (item.itemId) {
                    R.id.download -> setSelectedItemsWanted(true)
                    R.id.not_download -> setSelectedItemsWanted(false)
                    R.id.high_priority -> setSelectedItemsPriority(TorrentFilesTree.Item.Priority.High)
                    R.id.normal_priority -> setSelectedItemsPriority(TorrentFilesTree.Item.Priority.Normal)
                    R.id.low_priority -> setSelectedItemsPriority(TorrentFilesTree.Item.Priority.Low)
                    R.id.rename -> {
                        val i = getItem(selectionTracker.getFirstSelectedPosition())!!
                        filesTree.getItemNamePath(i)?.let { path -> navigateToRenameDialog(path, i.name) }
                    }
                    else -> ret = false
                }
                ret
            } ?: false
        }
    }
}