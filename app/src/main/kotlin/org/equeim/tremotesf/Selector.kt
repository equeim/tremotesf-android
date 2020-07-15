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
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View

import androidx.annotation.CallSuper
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.RecyclerView

import java.util.Collections


private const val BUNDLE_KEY = "org.equeim.tremotesf.Selector"

typealias SelectorActionModeCallbackFactory<K> = (Selector<K>) -> Selector.ActionModeCallback<K>
typealias AdapterSelectionKeyGetter<K> = RecyclerView.Adapter<*>.(Int) -> K

abstract class Selector<K: Any>(private val activity: AppCompatActivity,
                                private val actionModeCallbackFactory: SelectorActionModeCallbackFactory<K>,
                                private val adapter: RecyclerView.Adapter<*>,
                                private val getSelectionKeyForPosition: AdapterSelectionKeyGetter<K>,
                                @PluralsRes private val titleStringId: Int) {
    @Suppress("LeakingThis")
    private val selectionKeysProvider = SelectionKeysProvider(this, adapter, getSelectionKeyForPosition)

    private val handler = Handler()
    private val updateActionModeCallback = Runnable(::updateActionMode)

    var actionMode: ActionMode? = null

    private val _selectedKeys = mutableSetOf<K>()
    val selectedKeys: Set<K>
        get() = _selectedKeys

    var hasHeaderItem = false

    val selectedCount: Int
        get() = selectedKeys.size

    val hasSelection: Boolean
        get() = selectedKeys.isNotEmpty()

    fun getSelectedPositionsUnsorted(): List<Int> {
        return selectedKeys.map(selectionKeysProvider::getPositionForKey)
    }

    fun getFirstSelectedPosition(): Int {
        return getSelectedPositionsUnsorted().min() ?: -1
    }

    fun isSelected(key: K): Boolean {
        return selectedKeys.contains(key)
    }

    fun toggleSelection(key: K, position: Int) {
        if (hasHeaderItem && position == 0) {
            return
        }

        _selectedKeys.apply {
            if (contains(key)) {
                remove(key)
            } else {
                add(key)
            }
        }

        adapter.notifyItemChanged(position)

        if (hasSelection) {
            updateActionMode()
        } else {
            actionMode?.finish()
        }
    }

    fun selectAll() {
        val allKeysSize = if (hasHeaderItem) {
            selectionKeysProvider.allKeys.size - 1
        } else {
            selectionKeysProvider.allKeys.size
        }
        if (selectedCount == allKeysSize) {
            return
        }

        val keys = if (hasHeaderItem) {
            selectionKeysProvider.allKeys.drop(1)
        } else {
            selectionKeysProvider.allKeys
        }
        _selectedKeys.clear()
        _selectedKeys.addAll(keys)

        if (hasHeaderItem) {
            adapter.notifyItemRangeChanged(1, selectedKeys.size)
        } else {
            adapter.notifyItemRangeChanged(0, selectedKeys.size)
        }

        updateActionMode()
    }

    fun clearSelection() {
        actionMode = null

        if (selectedKeys.isEmpty()) {
            return
        }

        _selectedKeys.clear()

        if (hasHeaderItem) {
            adapter.notifyItemRangeChanged(1, adapter.itemCount - 1)
        } else {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    fun startActionMode() {
        actionMode = activity.startSupportActionMode(actionModeCallbackFactory(this))
        updateActionMode()
    }

    private fun updateActionMode() {
        actionMode?.apply {
            title = activity.resources.getQuantityString(titleStringId,
                                                         selectedCount,
                                                         selectedCount)
            invalidate()
        }
    }

    private fun removeSelectionInProgress(key: K) {
        _selectedKeys.remove(key)
    }

    private fun finishRemovingSelection() {
        if (hasSelection) {
            if (!handler.hasCallbacks(updateActionModeCallback)) {
                handler.post(updateActionModeCallback)
            }
        } else {
            actionMode?.finish()
        }
    }

    fun saveInstanceState(outState: Bundle) {
        if (actionMode != null) {
            putKeysToBundle(outState)
        }
    }

    fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_KEY)) {
            val restored = getKeysFromBundle(savedInstanceState)
            _selectedKeys.addAll(selectionKeysProvider.allKeys.filter(restored::contains))
            if (hasSelection) {
                startActionMode()
            }
        }
    }

    abstract fun putKeysToBundle(bundle: Bundle)
    abstract fun getKeysFromBundle(bundle: Bundle): Set<K>

    @Suppress("LeakingThis")
    abstract class ViewHolder<K : Any>(protected val selector: Selector<K>,
                                       itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        private val selectedBackground: View = itemView.findViewById(R.id.selected_background_view)

        init {
            itemView.setOnClickListener {
                if (selector.hasSelection && !(selector.hasHeaderItem && adapterPosition == 0)) {
                    selector.toggleSelection(getSelectionKey(), adapterPosition)
                } else {
                    onClick(it)
                }
            }
            itemView.setOnLongClickListener(this)
        }

        @CallSuper
        open fun update() {
            selectedBackground.isActivated = selector.isSelected(getSelectionKey())
        }

        final override fun onLongClick(view: View): Boolean {
            if (selector.hasSelection || (selector.hasHeaderItem && adapterPosition == 0)) {
                return false
            }
            selector.toggleSelection(getSelectionKey(), adapterPosition)
            selector.startActionMode()
            return true
        }

        private fun getSelectionKey(): K {
            return selector.selectionKeysProvider.getKeyForPosition(adapterPosition)
        }
    }

    abstract class ActionModeCallback<K : Any>(protected val selector: Selector<K>) : ActionMode.Callback {
        protected val activity = selector.activity

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selector.clearSelection()
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == R.id.select_all) {
                selector.selectAll()
                return true
            }
            return false
        }
    }

    private class SelectionKeysProvider<K: Any>(private val selector: Selector<K>,
                                                private val adapter: RecyclerView.Adapter<*>,
                                                private val getSelectionKeyForPosition: AdapterSelectionKeyGetter<K>) {
        private val positionToKey = ArrayList<K>()
        private val keyToPosition = mutableMapOf<K, Int>()

        val allKeys: List<K>
            get() = positionToKey

        init {
            val observer = object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        positionToKey.ensureCapacity(positionToKey.size + itemCount)
                        for (position in positionStart until (positionStart + itemCount)) {
                            val key = adapter.getSelectionKeyForPosition(position)
                            positionToKey.add(position, key)
                            keyToPosition[key] = position
                        }
                    }
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        for (position in (positionStart + itemCount - 1) downTo positionStart) {
                            val key = positionToKey[position]
                            positionToKey.removeAt(position)
                            keyToPosition.remove(key)

                            selector.removeSelectionInProgress(key)
                        }
                        selector.finishRemovingSelection()
                    }
                }

                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        val changedPositions = positionToKey.moveItems(fromPosition, toPosition, itemCount)
                        for (position in changedPositions) {
                            val key = positionToKey[position]
                            keyToPosition[key] = position
                        }
                    }
                }

                override fun onChanged() {
                    positionToKey.clear()
                    keyToPosition.clear()
                    onItemRangeInserted(0, adapter.itemCount)
                }
            }
            observer.onChanged()
            adapter.registerAdapterDataObserver(observer)
        }

        fun getKeyForPosition(position: Int): K {
            return positionToKey[position]
        }

        fun getPositionForKey(key: K): Int {
            return requireNotNull(keyToPosition[key])
        }

        /**
         * Moves block of items inside list
         * @param fromPosition position of first item in block
         * @param toPosition new position of first item in block
         * @param itemCount count of items in block
         * @return range of positions which items were changed
         */
        private fun MutableList<*>.moveItems(fromPosition: Int, toPosition: Int, itemCount: Int): IntRange {
            val changedFrom: Int
            val changedTo: Int
            val distance: Int
            if (toPosition >= fromPosition) {
                changedFrom = fromPosition
                changedTo = toPosition + itemCount
                distance = -itemCount
            } else {
                changedFrom = toPosition
                changedTo = fromPosition + itemCount
                distance = itemCount
            }
            Collections.rotate(subList(changedFrom, changedTo), distance)
            return changedFrom until changedTo
        }
    }
}

class IntSelector(activity: AppCompatActivity,
                  actionModeCallbackFactory: SelectorActionModeCallbackFactory<Int>,
                  @PluralsRes titleStringId: Int,
                  adapter: RecyclerView.Adapter<*>,
                  getSelectionKeyForPosition: AdapterSelectionKeyGetter<Int>) : Selector<Int>(activity, actionModeCallbackFactory, adapter, getSelectionKeyForPosition, titleStringId) {
    override fun getKeysFromBundle(bundle: Bundle): Set<Int> {
        return bundle.getIntArray(BUNDLE_KEY)?.toSet() ?: emptySet()
    }

    override fun putKeysToBundle(bundle: Bundle) {
        bundle.putIntArray(BUNDLE_KEY, selectedKeys.toIntArray())
    }
}

class StringSelector(activity: AppCompatActivity,
                     actionModeCallbackFactory: SelectorActionModeCallbackFactory<String>,
                     @PluralsRes titleStringId: Int,
                     adapter: RecyclerView.Adapter<*>,
                     getSelectionKeyForPosition: AdapterSelectionKeyGetter<String>) : Selector<String>(activity, actionModeCallbackFactory, adapter, getSelectionKeyForPosition, titleStringId) {
    override fun getKeysFromBundle(bundle: Bundle): Set<String> {
        return bundle.getStringArray(BUNDLE_KEY)?.toSet() ?: emptySet()
    }

    override fun putKeysToBundle(bundle: Bundle) {
        bundle.putStringArray(BUNDLE_KEY, selectedKeys.toTypedArray())
    }
}
