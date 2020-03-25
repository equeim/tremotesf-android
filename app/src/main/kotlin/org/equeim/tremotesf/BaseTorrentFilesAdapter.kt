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

package org.equeim.tremotesf

import java.util.Comparator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView

import org.equeim.libtremotesf.TorrentFile
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.TristateCheckbox

import kotlinx.android.synthetic.main.torrent_file_list_item.view.*


private const val BUNDLE_KEY = "org.equeim.tremotesf.LocalTorrentFilesAdapter.currentDirectoryPath"

abstract class BaseTorrentFilesAdapter(rootDirectory: Directory,
                                       activity: AppCompatActivity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    protected companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    var rootDirectory = rootDirectory
        private set

    protected var currentDirectory = rootDirectory

    protected val currentItems = mutableListOf<Item>()

    protected val hasHeaderItem: Boolean
        get() = (currentDirectory !== rootDirectory)

    private val comparator = object : Comparator<Item> {
        private val nameComparator = AlphanumericComparator()

        override fun compare(item1: Item,
                             item2: Item): Int {
            if (item1::class.java == item2::class.java) {
                return nameComparator.compare(item1.name, item2.name)
            }
            if (item1 is Directory) {
                return -1
            }
            return 1
        }
    }

    val selector = IntSelector(activity,
                               ActionModeCallback(),
                               this,
                               currentItems,
                               Item::row,
                               R.plurals.files_selected)

    override fun getItemCount(): Int {
        if (hasHeaderItem) {
            return currentItems.size + 1
        }
        return currentItems.size
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
            holder as BaseItemHolder

            val item = currentItems[if (hasHeaderItem) position - 1 else position]

            holder.item = item

            holder.iconView.setImageLevel(if (item is Directory) 0 else 1)

            holder.nameTextView.text = item.name

            holder.checkBox.state = when (item.wantedState) {
                Item.WantedState.Wanted -> TristateCheckbox.State.Checked
                Item.WantedState.Unwanted -> TristateCheckbox.State.Unchecked
                Item.WantedState.Mixed -> TristateCheckbox.State.Indeterminate
            }
            holder.checkBox.isEnabled = (selector.actionMode == null)

            holder.updateSelectedBackground()
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (hasHeaderItem && (position == 0)) {
            return TYPE_HEADER
        }
        return TYPE_ITEM
    }

    private fun navigateDown(item: Item) {
        if (item is Directory) {
            navigateTo(item)
        }
    }

    fun navigateUp(): Boolean {
        if (hasHeaderItem) {
            navigateTo(currentDirectory.parentDirectory!!)
            return true
        }
        return false
    }

    private fun navigateTo(directory: Directory) {
        val hadHeaderItem = hasHeaderItem

        currentDirectory = directory
        val count = currentItems.size
        currentItems.clear()
        if (hadHeaderItem) {
            if (hasHeaderItem) {
                notifyItemRangeRemoved(1, count)
            } else {
                notifyItemRangeRemoved(0, count + 1)
            }
        } else {
            notifyItemRangeRemoved(0, count)
        }
        currentItems.addAll(directory.children.sortedWith(comparator))
        if (hasHeaderItem) {
            notifyItemRangeInserted(1, currentItems.size)
        } else {
            notifyItemRangeInserted(0, currentItems.size)
        }
        selector.hasHeaderItem = hasHeaderItem
    }

    fun saveInstanceState(outState: Bundle) {
        val path = mutableListOf<Int>()
        var directory: Directory? = currentDirectory
        while (directory != null && directory != rootDirectory) {
            path.add(0, directory.row)
            directory = directory.parentDirectory
        }
        outState.putIntArray(BUNDLE_KEY, path.toIntArray())
        selector.saveInstanceState(outState)
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
            currentItems.addAll(rootDirectory.children.sortedWith(comparator))
            notifyItemRangeInserted(0, currentItems.size)
        }

        selector.restoreInstanceState(savedInstanceState)
    }

    private fun getItemPosition(item: Item): Int {
        val index = currentItems.indexOf(item)
        if (index != -1 && hasHeaderItem) {
            return index + 1
        }
        return index
    }

    private fun setSelectedItemsWanted(wanted: Boolean) {
        val ids = mutableListOf<Int>()
        for (item in selector.selectedItems) {
            item.setWanted(wanted, ids)
            notifyItemChanged(getItemPosition(item))
        }
        onSetFilesWanted(ids.toIntArray(), wanted)
    }

    private fun setSelectedItemsPriority(priority: Item.Priority) {
        val ids = mutableListOf<Int>()
        for (item in selector.selectedItems) {
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
            val index = currentItems.indexOf(item)
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
                if (selector.actionMode == null) {
                    navigateUp()
                }
            }
        }
    }

    protected abstract class BaseItemHolder(private val adapter: BaseTorrentFilesAdapter,
                                            selector: Selector<Item, Int>,
                                            itemView: View) : Selector.ViewHolder<Item>(
            selector,
            itemView) {
        override lateinit var item: Item

        val iconView = itemView.icon_view!!
        val nameTextView = itemView.name_text_view!!
        val checkBox = itemView.check_box!!

        init {
            checkBox.setOnClickListener {
                val ids = mutableListOf<Int>()
                item.setWanted(checkBox.isChecked, ids)
                adapter.onSetFilesWanted(ids.toIntArray(), checkBox.isChecked)
            }
        }

        override fun onClick(view: View) {
            if (selector.actionMode == null) {
                adapter.navigateDown(item)
            } else {
                super.onClick(view)
            }
        }
    }

    private inner class ActionModeCallback : Selector.ActionModeCallback<Item>() {
        private var downloadItem: MenuItem? = null
        private var notDownloadItem: MenuItem? = null
        private var lowPriorityItem: MenuItem? = null
        private var normalPriorityItem: MenuItem? = null
        private var highPriorityItem: MenuItem? = null
        private var mixedPriorityItem: MenuItem? = null
        private var renameItem: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.base_torrent_files_context_menu, menu)

            downloadItem = menu.findItem(R.id.download)
            notDownloadItem = menu.findItem(R.id.not_download)
            lowPriorityItem = menu.findItem(R.id.low_priority)
            normalPriorityItem = menu.findItem(R.id.normal_priority)
            highPriorityItem = menu.findItem(R.id.high_priority)
            mixedPriorityItem = menu.findItem(R.id.mixed_priority)
            renameItem = menu.findItem(R.id.rename)

            if (hasHeaderItem) {
                notifyItemRangeChanged(1, currentItems.size)
            } else {
                notifyItemRangeChanged(0, currentItems.size)
            }

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (!selector.hasSelection) {
                return super.onPrepareActionMode(mode, menu)
            }

            super.onPrepareActionMode(mode, menu)

            if (selector.selectedCount == 1) {
                val first = selector.selectedItems.first()
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
                    val file = selector.selectedItems.first()

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

            selector.actionMode?.invalidate()

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