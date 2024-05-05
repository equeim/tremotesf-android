// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.bindingAdapterPositionOrNull
import org.equeim.tremotesf.ui.utils.submitListAwait
import java.lang.ref.WeakReference


abstract class BaseTorrentFilesAdapter(
    private val filesTree: TorrentFilesTree,
    private val fragment: Fragment,
) : AsyncLoadingListAdapter<TorrentFilesTree.Item?, RecyclerView.ViewHolder>(ItemCallback()) {
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

    suspend fun update(items: List<TorrentFilesTree.Item?>?) {
        submitListAwait(items)
        selectionTracker.commitAdapterUpdate()
    }

    override fun onStateRestored() {
        selectionTracker.restoreInstanceState()
    }

    private fun setSelectedItemsWanted(wanted: Boolean) {
        val nodeIndexes =
            selectionTracker.mapSelectedPositionsToList { getItem(it)!!.nodePath.last() }
        filesTree.setItemsWanted(nodeIndexes, wanted)
    }

    private fun setSelectedItemsPriority(priority: TorrentFilesTree.Item.Priority) {
        val nodeIndexes =
            selectionTracker.mapSelectedPositionsToList { getItem(it)!!.nodePath.last() }
        filesTree.setItemsPriority(nodeIndexes, priority)
    }

    protected abstract fun navigateToRenameDialog(path: String, name: String)

    private class ItemCallback : DiffUtil.ItemCallback<TorrentFilesTree.Item?>() {
        override fun areItemsTheSame(
            oldItem: TorrentFilesTree.Item,
            newItem: TorrentFilesTree.Item,
        ): Boolean {
            return oldItem === newItem || oldItem.nodePath.contentEquals(newItem.nodePath)
        }

        override fun areContentsTheSame(
            oldItem: TorrentFilesTree.Item,
            newItem: TorrentFilesTree.Item,
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
        itemView: View,
    ) : SelectionTracker.ViewHolder<Int>(
        selectionTracker,
        itemView
    ) {
        private val iconView: ImageView = itemView.findViewById(R.id.icon_view)
        private val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.check_box)

        private var isDirectory: Boolean? = null

        init {
            checkBox.setOnClickListener {
                bindingAdapterPositionOrNull?.let(adapter::getItem)?.let { item ->
                    adapter.filesTree.setItemsWanted(
                        listOf(item.nodePath.last()),
                        checkBox.isChecked
                    )
                }
            }
        }

        override fun onClick(view: View) {
            bindingAdapterPositionOrNull?.let(adapter::getItem)?.let(adapter.filesTree::navigateDown)
        }

        override fun update() {
            super.update()

            val item = bindingAdapterPositionOrNull?.let(adapter::getItem) ?: return

            if (item.isDirectory != isDirectory) {
                isDirectory = item.isDirectory

                val resId = if (item.isDirectory) R.drawable.ic_folder_24dp else R.drawable.ic_insert_drive_file_24dp
                iconView.setImageResource(resId)

                val contentDescription =
                    if (item.isDirectory) R.string.directory_icon else R.string.file_icon
                iconView.contentDescription = itemView.context.getText(contentDescription)
            }

            nameTextView.text = item.name

            checkBox.checkedState = when (item.wantedState) {
                TorrentFilesTree.Item.WantedState.Wanted -> MaterialCheckBox.STATE_CHECKED
                TorrentFilesTree.Item.WantedState.Unwanted -> MaterialCheckBox.STATE_UNCHECKED
                TorrentFilesTree.Item.WantedState.Mixed -> MaterialCheckBox.STATE_INDETERMINATE
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
                return false
            }
            val adapter = this.adapter.get() ?: return false

            val (wantedState, priority) = computeWantedStateAndPriority(selectionTracker, adapter)

            downloadItem!!.isEnabled = when (wantedState) {
                TorrentFilesTree.Item.WantedState.Unwanted, TorrentFilesTree.Item.WantedState.Mixed -> true
                TorrentFilesTree.Item.WantedState.Wanted -> false
            }
            notDownloadItem!!.isEnabled = when (wantedState) {
                TorrentFilesTree.Item.WantedState.Wanted, TorrentFilesTree.Item.WantedState.Mixed -> true
                TorrentFilesTree.Item.WantedState.Unwanted -> false
            }
            when (priority) {
                TorrentFilesTree.Item.Priority.Low -> lowPriorityItem
                TorrentFilesTree.Item.Priority.Normal -> normalPriorityItem
                TorrentFilesTree.Item.Priority.High -> highPriorityItem
                TorrentFilesTree.Item.Priority.Mixed -> mixedPriorityItem
            }!!.isChecked = true
            mixedPriorityItem!!.isVisible = priority == TorrentFilesTree.Item.Priority.Mixed
            renameItem!!.isEnabled = selectionTracker.selectedCount == 1

            return true
        }

        private fun computeWantedStateAndPriority(selectionTracker: SelectionTracker<Int>, adapter: BaseTorrentFilesAdapter): Pair<TorrentFilesTree.Item.WantedState, TorrentFilesTree.Item.Priority> {
            var wantedState: TorrentFilesTree.Item.WantedState? = null
            var priority: TorrentFilesTree.Item.Priority? = null
            for (position in selectionTracker.getSelectedPositionsUnsorted()) {
                val item = adapter.getItem(position)!!
                if (wantedState == null) {
                    wantedState = item.wantedState
                    priority = item.priority
                } else {
                    if (item.priority != priority) {
                        priority = TorrentFilesTree.Item.Priority.Mixed
                    }
                    if (item.wantedState != wantedState) {
                        wantedState = TorrentFilesTree.Item.WantedState.Mixed
                    }
                    if (priority == TorrentFilesTree.Item.Priority.Mixed && wantedState == TorrentFilesTree.Item.WantedState.Mixed) {
                        break
                    }
                }
            }
            return wantedState!! to priority!!
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
                        selectionTracker.getFirstSelectedPosition()?.let(::getItem)?.let { firstItem ->
                            filesTree.getItemNamePath(firstItem)?.let { path -> navigateToRenameDialog(path, firstItem.name) }
                        }
                    }

                    else -> ret = false
                }
                ret
            } ?: false
        }
    }
}
