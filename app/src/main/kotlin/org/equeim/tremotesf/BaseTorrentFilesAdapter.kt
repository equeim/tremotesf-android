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

package org.equeim.tremotesf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView

import org.equeim.libtremotesf.TorrentFile
import org.equeim.tremotesf.utils.TristateCheckbox


private const val BUNDLE_KEY = "org.equeim.tremotesf.LocalTorrentFilesAdapter.currentDirectoryPath"

abstract class BaseTorrentFilesAdapter(rootDirectory: Directory,
                                       private val activity: AppCompatActivity) : BaseFilesAdapter<BaseTorrentFilesAdapter.Item, BaseTorrentFilesAdapter.Directory>(rootDirectory) {
    var rootDirectory = rootDirectory
        private set

    override val hasHeaderItem: Boolean
        get() = (currentDirectory !== rootDirectory)

    lateinit var selectionTracker: SelectionTracker<Int>
        private set

    override fun getItemParentDirectory(item: Item): Directory? = item.parentDirectory
    override fun getItemName(item: Item): String = item.name
    override fun itemIsDirectory(item: Item): Boolean = item is Directory

    override fun getDirectoryChildren(directory: Directory): List<Item> {
        return directory.children
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        selectionTracker = createSelectionTrackerInt(activity,
                                                     ::ActionModeCallback,
                                                     R.plurals.files_selected,
                                                     this) {
            if (it == 0 && hasHeaderItem) SelectionTracker.SELECTION_KEY_UNSELECTABLE_INT else getItem(it).row
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(LayoutInflater.from(parent.context).inflate(R.layout.up_list_item,
                                                                            parent,
                                                                            false))
        }
        throw InvalidViewTypeException()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == TYPE_ITEM) {
            (holder as BaseItemHolder).update()
        }
    }

    private fun navigateDown(item: Item) {
        if (item is Directory) {
            navigateTo(item)
        }
    }

    fun saveInstanceState(outState: Bundle) {
        val path = mutableListOf<Int>()
        var directory: Directory? = currentDirectory
        while (directory != null && directory != rootDirectory) {
            path.add(0, directory.row)
            directory = directory.parentDirectory
        }
        outState.putIntArray(BUNDLE_KEY, path.toIntArray())
        selectionTracker.saveInstanceState(outState)
    }

    fun restoreInstanceState(savedInstanceState: Bundle?, newRootDirectory: Directory? = null) {
        if (newRootDirectory != null) {
            rootDirectory = newRootDirectory
            currentDirectory = rootDirectory
        }

        var root = true

        if (savedInstanceState != null) {
            savedInstanceState.getIntArray(BUNDLE_KEY)?.let { path ->
                var directory: Directory? = rootDirectory
                for (row in path) {
                    directory = directory?.children?.find { it.row == row } as? Directory
                    if (directory == null) {
                        break
                    }
                }
                if (directory !== rootDirectory && directory != null) {
                    navigateTo(directory)
                    root = false
                }
            }
        }

        if (root) {
            items = rootDirectory.children.sortedWith(comparator)
            notifyItemRangeInserted(0, items.size)
        }

        selectionTracker.restoreInstanceState(savedInstanceState)
    }

    private fun getItemPosition(item: Item): Int {
        val index = items.indexOf(item)
        if (index != -1 && hasHeaderItem) {
            return index + 1
        }
        return index
    }

    private fun setSelectedItemsWanted(wanted: Boolean) {
        val ids = mutableListOf<Int>()
        for (position in selectionTracker.getSelectedPositionsUnsorted()) {
            val item = getItem(position)
            item.setWanted(wanted, ids)
            notifyItemChanged(getItemPosition(item))
        }
        onSetFilesWanted(ids.toIntArray(), wanted)
    }

    private fun setSelectedItemsPriority(priority: Item.Priority) {
        val ids = mutableListOf<Int>()
        for (position in selectionTracker.getSelectedPositionsUnsorted()) {
            val item = getItem(position)
            item.setPriority(priority, ids)
        }
        onSetFilesPriority(ids.toIntArray(), priority)
    }

    protected open fun onSetFilesWanted(ids: IntArray, wanted: Boolean) {

    }

    protected open fun onSetFilesPriority(ids: IntArray, priority: Item.Priority) {

    }

    protected abstract fun onNavigateToRenameDialog(args: Bundle)

    fun fileRenamed(path: String, newName: String) {
        val pathParts = path.split('/').filter(String::isNotEmpty)
        var item: Item? = rootDirectory
        for (part in pathParts) {
            item = (item as Directory).children.find { it.name == part }
            if (item == null) {
                break
            }
        }
        if (item == rootDirectory) {
            item = null
        }

        if (item != null) {
            item.name = newName
            val index = items.indexOf(item)
            if (index != -1) {
                if (hasHeaderItem) {
                    notifyItemChanged(index + 1)
                } else {
                    notifyItemChanged(index)
                }
            }
        }
    }

    private inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                if (selectionTracker.actionMode == null) {
                    navigateUp()
                }
            }
        }
    }

    protected abstract class BaseItemHolder(private val adapter: BaseTorrentFilesAdapter,
                                            selectionTracker: SelectionTracker<Int>,
                                            itemView: View) : SelectionTracker.ViewHolder<Int>(
            selectionTracker,
            itemView) {

        private val iconView: ImageView = itemView.findViewById(R.id.icon_view)
        private val nameTextView: TextView = itemView.findViewById(R.id.name_text_view)
        private val checkBox: TristateCheckbox = itemView.findViewById(R.id.check_box)

        init {
            checkBox.setOnClickListener {
                val ids = mutableListOf<Int>()
                adapter.getItem(adapterPosition).setWanted(checkBox.isChecked, ids)
                adapter.onSetFilesWanted(ids.toIntArray(), checkBox.isChecked)
            }
        }

        override fun onClick(view: View) {
            adapter.navigateDown(adapter.getItem(adapterPosition))
        }

        override fun update() {
            super.update()

            val item = adapter.getItem(adapterPosition)

            iconView.setImageLevel(if (item is Directory) 0 else 1)

            nameTextView.text = item.name

            checkBox.state = when (item.wantedState) {
                Item.WantedState.Wanted -> TristateCheckbox.State.Checked
                Item.WantedState.Unwanted -> TristateCheckbox.State.Unchecked
                Item.WantedState.Mixed -> TristateCheckbox.State.Indeterminate
            }
            checkBox.isEnabled = (selectionTracker.actionMode == null)
        }
    }

    private inner class ActionModeCallback(selectionTracker: SelectionTracker<Int>) : SelectionTracker.ActionModeCallback<Int>(selectionTracker) {
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

            if (hasHeaderItem) {
                notifyItemRangeChanged(1, items.size)
            } else {
                notifyItemRangeChanged(0, items.size)
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (!selectionTracker.hasSelection) {
                return super.onPrepareActionMode(mode, menu)
            }

            super.onPrepareActionMode(mode, menu)

            if (selectionTracker.selectedCount == 1) {
                val first = getItem(selectionTracker.getFirstSelectedPosition())
                val wanted = (first.wantedState == Item.WantedState.Wanted)
                downloadItem!!.isEnabled = !wanted
                notDownloadItem!!.isEnabled = wanted
                when (first.priority) {
                    Item.Priority.Low -> lowPriorityItem
                    Item.Priority.Normal -> normalPriorityItem
                    Item.Priority.High -> highPriorityItem
                    Item.Priority.Mixed -> mixedPriorityItem
                }!!.isChecked = true
                mixedPriorityItem!!.isVisible = (first.priority == Item.Priority.Mixed)
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

            when (item.itemId) {
                R.id.download -> setSelectedItemsWanted(true)
                R.id.not_download -> setSelectedItemsWanted(false)
                R.id.high_priority -> setSelectedItemsPriority(Item.Priority.High)
                R.id.normal_priority -> setSelectedItemsPriority(Item.Priority.Normal)
                R.id.low_priority -> setSelectedItemsPriority(Item.Priority.Low)
                R.id.rename -> {
                    val file = getItem(selectionTracker.getFirstSelectedPosition())

                    val pathParts = mutableListOf<String>()
                    var i: Item? = file
                    while (i != null && i != rootDirectory) {
                        pathParts.add(0, i.name)
                        i = i.parentDirectory
                    }

                    onNavigateToRenameDialog(bundleOf(TorrentFileRenameDialogFragment.FILE_PATH to pathParts.joinToString("/"),
                                                      TorrentFileRenameDialogFragment.FILE_NAME to file.name))
                }
                else -> return false
            }

            selectionTracker.actionMode?.invalidate()

            return true
        }
    }


    abstract class Item(val row: Int,
                        var parentDirectory: Directory?,
                        var name: String) {
        enum class WantedState {
            Wanted,
            Unwanted,
            Mixed
        }

        enum class Priority {
            Low,
            Normal,
            High,
            Mixed;

            companion object {
                fun fromTorrentFilePriority(priority: Int): Priority {
                    return when (priority) {
                        TorrentFile.Priority.LowPriority -> Low
                        TorrentFile.Priority.NormalPriority -> Normal
                        TorrentFile.Priority.HighPriority -> High
                        else -> Normal
                    }
                }
            }

            fun toTorrentFilePriority(): Int {
                return when (this) {
                    Low -> TorrentFile.Priority.LowPriority
                    Normal -> TorrentFile.Priority.NormalPriority
                    High -> TorrentFile.Priority.HighPriority
                    else -> TorrentFile.Priority.NormalPriority
                }
            }
         }

        abstract val size: Long
        abstract val completedSize: Long
        val progress: Float
            get() {
                val bytes = size
                if (bytes == 0L) {
                    return 0.0f
                }
                return (completedSize.toFloat() / bytes.toFloat())
            }

        abstract val wantedState: WantedState
        abstract fun setWanted(wanted: Boolean, ids: MutableList<Int>? = null)

        abstract val priority: Priority

        abstract fun setPriority(priority: Priority, ids: MutableList<Int>? = null)

        abstract val changed: Boolean
    }

    class Directory(row: Int,
                    parentDirectory: Directory?,
                    name: String) : Item(row, parentDirectory, name) {
        constructor() : this(-1, null, "")

        override val size: Long
            get() {
                var bytes = 0L
                for (item in children) {
                    bytes += item.size
                }
                return bytes
            }

        override val completedSize: Long
            get() {
                var bytes = 0L
                for (item in children) {
                    bytes += item.completedSize
                }
                return bytes
            }

        override val wantedState: WantedState
            get() {
                val first = children.first().wantedState
                if (first == WantedState.Mixed) {
                    return first
                }
                for (item in children.drop(1)) {
                    if (item.wantedState != first) {
                        return WantedState.Mixed
                    }
                }
                return first
            }

        override fun setWanted(wanted: Boolean, ids: MutableList<Int>?) {
            for (item in children) {
                item.setWanted(wanted, ids)
            }
        }

        override val priority: Priority
            get() {
                val first = children.first().priority
                if (first == Priority.Mixed) {
                    return first
                }
                for (item in children.drop(1)) {
                    if (item.priority != first) {
                        return Priority.Mixed
                    }
                }
                return first
            }

        override fun setPriority(priority: Priority, ids: MutableList<Int>?) {
            for (item in children) {
                item.setPriority(priority, ids)
            }
        }

        override val changed: Boolean
            get() = children.any(Item::changed)

        val children = mutableListOf<Item>()
        val childrenMap = mutableMapOf<String, Item>()

        fun addChild(child: Item) {
            children.add(child)
            childrenMap[child.name] = child
        }

        fun clearChildren() {
            children.clear()
            childrenMap.clear()
        }
    }

    class File(row: Int,
               parentDirectory: Directory?,
               name: String,
               override val size: Long,
               val id: Int) : Item(row, parentDirectory, name) {
        override var completedSize = 0L
        override var wantedState = WantedState.Unwanted

        override fun setWanted(wanted: Boolean, ids: MutableList<Int>?) {
            wantedState = if (wanted) {
                WantedState.Wanted
            } else {
                WantedState.Unwanted
            }
            ids?.add(id)
        }

        override var priority = Priority.Normal

        override fun setPriority(priority: Priority, ids: MutableList<Int>?) {
            this.priority = priority
            ids?.add(id)
        }

        override var changed = false
    }

    class InvalidViewTypeException : Exception()
}