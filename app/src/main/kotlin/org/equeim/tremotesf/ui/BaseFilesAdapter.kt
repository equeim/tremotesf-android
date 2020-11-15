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

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import java.util.Comparator

abstract class BaseFilesAdapter<T : Any, D : T>  : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    protected companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    protected abstract var currentDirectory: D
    var items = emptyList<T>()
        protected set

    protected abstract val hasHeaderItem: Boolean

    protected val comparator = object : Comparator<T> {
        private val nameComparator = AlphanumericComparator()

        override fun compare(item1: T, item2: T): Int {
            if (itemIsDirectory(item1) == itemIsDirectory(item2)) {
                return nameComparator.compare(getItemName(item1), getItemName(item2))
            }
            if (itemIsDirectory(item1)) {
                return -1
            }
            return 1
        }
    }

    protected abstract fun getItemParentDirectory(item: T): D?
    protected abstract fun getItemName(item: T): String
    protected abstract fun itemIsDirectory(item: T): Boolean
    protected abstract fun getDirectoryChildren(directory: D): List<T>

    override fun getItemCount(): Int {
        if (hasHeaderItem) {
            return items.size + 1
        }
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        if (hasHeaderItem && (position == 0)) {
            return TYPE_HEADER
        }
        return TYPE_ITEM
    }

    fun getItem(position: Int): T {
        return items[if (hasHeaderItem) position - 1 else position]
    }

    open fun navigateUp(): Boolean {
        if (hasHeaderItem) {
            val directory = getItemParentDirectory(currentDirectory)
            if (directory != null) {
                val children = getDirectoryChildren(directory)
                if (children.isNotEmpty()) {
                    navigateTo(directory, children)
                }
                return true
            }
        }
        return false
    }

    @CallSuper
    protected open fun navigateTo(directory: D, directoryChildren: List<T>) {
        val hadHeaderItem = hasHeaderItem

        currentDirectory = directory
        val oldCount = items.size
        items = emptyList()
        if (hadHeaderItem) {
            if (hasHeaderItem) {
                notifyItemRangeRemoved(1, oldCount)
            } else {
                notifyItemRangeRemoved(0, oldCount + 1)
            }
        } else {
            notifyItemRangeRemoved(0, oldCount)
        }
        items = directoryChildren.sortedWith(comparator)
        if (hasHeaderItem) {
            if (hadHeaderItem) {
                notifyItemRangeInserted(1, items.size)
            } else {
                notifyItemRangeInserted(0, items.size + 1)
            }
        } else {
            notifyItemRangeInserted(0, items.size)
        }
    }

    protected fun navigateTo(directory: D) {
        navigateTo(directory, getDirectoryChildren(directory))
    }
}