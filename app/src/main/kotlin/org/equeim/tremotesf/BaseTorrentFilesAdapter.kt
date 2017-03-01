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

package org.equeim.tremotesf

import java.io.Serializable
import java.text.Collator
import java.util.Comparator

import android.content.Context

import android.os.Bundle
import android.util.AttributeSet

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.View

import android.widget.ImageView
import android.widget.TextView

import android.support.v4.widget.CompoundButtonCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.AppCompatCheckBox
import android.support.v7.widget.RecyclerView

import com.amjjd.alphanum.AlphanumericComparator


private const val BUNDLE_KEY = "org.equeim.tremotesf.LocalTorrentFilesAdapter.currentDirectoryPath"

abstract class BaseTorrentFilesAdapter(protected val rootDirectory: Directory) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    protected companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    protected var currentDirectory = rootDirectory

    protected val currentItems = mutableListOf<Item>()

    protected val hasHeaderItem: Boolean
        get() {
            return (currentDirectory !== rootDirectory)
        }

    protected val comparator = object : Comparator<Item> {
        private val nameComparator = AlphanumericComparator(Collator.getInstance())

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

    lateinit var selector: Selector<Item, Int>

    protected fun initSelector(activity: AppCompatActivity,
                               actionModeCallback: BaseActionModeCallback) {
        selector = Selector(activity,
                            actionModeCallback,
                            this,
                            currentItems,
                            Item::row,
                            R.plurals.files_selected)
    }

    override fun getItemCount(): Int {
        if (hasHeaderItem) {
            return currentItems.size + 1
        }
        return currentItems.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(LayoutInflater.from(parent.context).inflate(R.layout.up_list_item,
                                                                            parent,
                                                                            false))
        }
        return null
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == TYPE_ITEM) {
            holder as BaseItemHolder

            val item: Item
            if (hasHeaderItem) {
                item = currentItems[position - 1]
            } else {
                item = currentItems[position]
            }

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

    protected fun navigateTo(directory: Directory) {
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
        var directory = currentDirectory
        while (directory != rootDirectory) {
            path.add(0, directory.row)
            directory = directory.parentDirectory!!
        }
        outState.putSerializable(BUNDLE_KEY, path as Serializable)
        selector.saveInstanceState(outState)
    }

    fun restoreInstanceState(savedInstanceState: Bundle?) {
        var root = true

        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_KEY)) {
            @Suppress("UNCHECKED_CAST")
            val path = savedInstanceState.getSerializable(BUNDLE_KEY) as List<Int>
            var directory: Directory? = rootDirectory
            for (row in path) {
                directory = directory!!.children.find { it.row == row } as? Directory
                if (directory == null) {
                    break
                }
            }
            if (directory !== rootDirectory && directory != null) {
                navigateTo(directory)
                root = false
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

    protected open fun setSelectedItemsWanted(wanted: Boolean) {
        for (item in selector.selectedItems) {
            item.setWanted(wanted)
            notifyItemChanged(getItemPosition(item))
        }
    }

    protected open fun setSelectedItemsPriority(priority: Item.Priority) {
        for (item in selector.selectedItems) {
            item.priority = priority
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
        lateinit override var item: Item

        val iconView = itemView.findViewById(R.id.icon_view) as ImageView
        val nameTextView = itemView.findViewById(R.id.name_text_view) as TextView
        val checkBox = itemView.findViewById(R.id.check_box) as TristateCheckbox

        init {
            checkBox.setOnClickListener {
                item.setWanted(checkBox.isChecked)
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

    class TristateCheckbox(context: Context,
                           attrs: AttributeSet?) : AppCompatCheckBox(context, attrs) {
        constructor(context: Context) : this(context, null)

        enum class State {
            Checked,
            Unchecked,
            Indeterminate
        }

        override fun toggle() {
            state = when (state) {
                State.Checked -> State.Unchecked
                State.Unchecked -> State.Checked
                State.Indeterminate -> State.Checked
            }
        }

        var state = State.Unchecked
            set(value) {
                if (value != field) {
                    field = value
                    isChecked = (value != State.Unchecked)
                    CompoundButtonCompat.getButtonDrawable(this)?.alpha = if (value == State.Indeterminate) {
                        127
                    } else {
                        255
                    }
                }
            }
    }

    protected abstract inner class BaseActionModeCallback : Selector.ActionModeCallback<Item>() {
        private var downloadItem: MenuItem? = null
        private var notDownloadItem: MenuItem? = null
        private var lowPriorityItem: MenuItem? = null
        private var normalPriorityItem: MenuItem? = null
        private var highPriorityItem: MenuItem? = null
        private var mixedPriorityItem: MenuItem? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.base_torrent_files_context_menu, menu)

            downloadItem = menu.findItem(R.id.download)
            notDownloadItem = menu.findItem(R.id.not_download)
            lowPriorityItem = menu.findItem(R.id.low_priority)
            normalPriorityItem = menu.findItem(R.id.normal_priority)
            highPriorityItem = menu.findItem(R.id.high_priority)
            mixedPriorityItem = menu.findItem(R.id.mixed_priority)

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
            } else {
                downloadItem!!.isEnabled = true
                notDownloadItem!!.isEnabled = true
                mixedPriorityItem!!.isVisible = true
                mixedPriorityItem!!.isChecked = true
            }

            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            super.onDestroyActionMode(mode)
            downloadItem = null
            notDownloadItem = null
            lowPriorityItem = null
            normalPriorityItem = null
            highPriorityItem = null
            mixedPriorityItem = null

            if (hasHeaderItem) {
                notifyItemRangeChanged(1, currentItems.size)
            } else {
                notifyItemRangeChanged(0, currentItems.size)
            }
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
                else -> return false
            }

            selector.actionMode!!.invalidate()

            return true
        }
    }


    abstract class Item(val row: Int,
                        val parentDirectory: Directory?,
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
            Mixed
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
        abstract fun setWanted(wanted: Boolean)

        abstract var priority: Priority

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

        override fun setWanted(wanted: Boolean) {
            for (item in children) {
                item.setWanted(wanted)
            }
        }

        override var priority = Priority.Normal
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
            set(value) {
                field = value
                for (item in children) {
                    item.priority = value
                }
            }

        override val changed: Boolean
            get() {
                for (item in children) {
                    if (item.changed) {
                        return true
                    }
                }
                return false
            }

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

        val childrenIds: List<Int>
            get() {
                val ids = mutableListOf<Int>()
                for (item in children) {
                    if (item is Directory) {
                        ids.addAll(item.childrenIds)
                    } else {
                        ids.add((item as File).id)
                    }
                }
                return ids
            }
    }

    class File(row: Int,
               parentDirectory: Directory,
               name: String,
               val id: Int) : Item(row, parentDirectory, name) {
        override var size = 0L
            set(value) {
                if (value != field) {
                    field = value
                    changed = true
                }
            }

        override var completedSize = 0L
            set(value) {
                if (value != field) {
                    field = value
                    changed = true
                }
            }

        override var wantedState = WantedState.Unwanted
            private set(value) {
                if (value != field) {
                    field = value
                    changed = true
                }
            }

        override fun setWanted(wanted: Boolean) {
            wantedState = if (wanted) {
                WantedState.Wanted
            } else {
                WantedState.Unwanted
            }
        }

        override var priority = Priority.Normal
            set(value) {
                if (value != field) {
                    field = value
                    changed = true
                }
            }

        override var changed = false
    }
}