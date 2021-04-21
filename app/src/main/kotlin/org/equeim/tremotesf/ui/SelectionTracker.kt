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

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View

import androidx.annotation.CallSuper
import androidx.annotation.PluralsRes
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistryOwner

import org.equeim.tremotesf.R

import java.lang.ref.WeakReference
import java.util.Collections


private const val BUNDLE_KEY = "org.equeim.tremotesf.ui.SelectionTracker"

typealias SelectionActionModeCallbackFactory<K> = (SelectionTracker<K>) -> SelectionTracker.ActionModeCallback<K>
typealias AdapterSelectionKeyGetter<K> = RecyclerView.Adapter<*>.(Int) -> K

class SelectionTracker<K : Any> private constructor(private val adapter: RecyclerView.Adapter<*>,
                                                    adapterCommitsUpdates: Boolean,
                                                    getSelectionKeyForPosition: AdapterSelectionKeyGetter<K>,
                                                    private val unselectableKey: K,

                                                    private val context: Context,

                                                    private val savedStateRegistryOwner: SavedStateRegistryOwner,
                                                    private val getSelectedKeysFromBundle: (Bundle) -> Set<K>?,
                                                    putSelectedKeysToBundle: Bundle.(Set<K>) -> Unit,
                                                    viewLifecycleOwner: LifecycleOwner,

                                                    private val activity: NavigationActivity,
                                                    private val actionModeCallbackFactory: SelectionActionModeCallbackFactory<K>,
                                                    @PluralsRes private val titleStringId: Int) {
    private constructor(adapter: RecyclerView.Adapter<*>,
                        adapterCommitsUpdates: Boolean,
                        getSelectionKeyForPosition: AdapterSelectionKeyGetter<K>,
                        unselectableKey: K,
                        fragment: Fragment,
                        getSelectedKeysFromBundle: (Bundle) -> Set<K>?,
                        putSelectedKeysToBundle: Bundle.(Set<K>) -> Unit,
                        actionModeCallbackFactory: SelectionActionModeCallbackFactory<K>,
                        @PluralsRes titleStringId: Int) : this(adapter,
            adapterCommitsUpdates,
            getSelectionKeyForPosition,
            unselectableKey,
            fragment.requireContext(),
            fragment,
            getSelectedKeysFromBundle,
            putSelectedKeysToBundle,
            fragment.viewLifecycleOwner,
            fragment.requireActivity() as NavigationActivity,
            actionModeCallbackFactory,
            titleStringId)

    companion object {
        const val SELECTION_KEY_UNSELECTABLE_INT = -1
        const val SELECTION_KEY_UNSELECTABLE_STRING = ""

        fun createForIntKeys(adapter: RecyclerView.Adapter<*>,
                             adapterCommitsUpdates: Boolean,
                             fragment: Fragment,
                             actionModeCallbackFactory: SelectionActionModeCallbackFactory<Int>,
                             @PluralsRes titleStringId: Int,
                             getSelectionKeyForPosition: AdapterSelectionKeyGetter<Int>): SelectionTracker<Int> {
            return SelectionTracker(adapter,
                    adapterCommitsUpdates,
                    getSelectionKeyForPosition,
                    SELECTION_KEY_UNSELECTABLE_INT,
                    fragment,
                    { it.getIntArray(null)?.toSet() },
                    { putIntArray(null, it.toIntArray()) },
                    actionModeCallbackFactory,
                    titleStringId)
        }

        fun createForStringKeys(adapter: RecyclerView.Adapter<*>,
                                adapterCommitsUpdates: Boolean,
                                fragment: Fragment,
                                actionModeCallbackFactory: SelectionActionModeCallbackFactory<String>,
                                @PluralsRes titleStringId: Int,
                                getSelectionKeyForPosition: AdapterSelectionKeyGetter<String>): SelectionTracker<String> {
            return SelectionTracker(adapter,
                    adapterCommitsUpdates,
                    getSelectionKeyForPosition,
                    SELECTION_KEY_UNSELECTABLE_STRING,
                    fragment,
                    { it.getStringArray(null)?.toSet() },
                    { putStringArray(null, it.toTypedArray()) },
                    actionModeCallbackFactory,
                    titleStringId)
        }
    }

    private var restoredInstanceState = false

    private val selectionKeysProvider = SelectionKeysProvider(this, adapter, adapterCommitsUpdates, getSelectionKeyForPosition)

    private val handler = SelectionTrackerHandler(this)

    private var actionMode: ActionMode? = null

    private val _selectedKeys = mutableSetOf<K>()
    val selectedKeys: Set<K>
        get() = _selectedKeys

    val selectedCount: Int
        get() = selectedKeys.size

    val hasSelection: Boolean
        get() = selectedKeys.isNotEmpty()

    init {
        savedStateRegistryOwner.savedStateRegistry.registerSavedStateProvider(BUNDLE_KEY) {
            Bundle().apply { putSelectedKeysToBundle(selectedKeys) }
        }
        // Saved state registry may outlive view (e.g. in case of Fragments) so we need to unregister
        // when view is destroyed, since our lifecycle is tied to adapter and therefore view
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                savedStateRegistryOwner.savedStateRegistry.unregisterSavedStateProvider(BUNDLE_KEY)
            }
        })
    }

    fun getSelectedPositionsUnsorted(): List<Int> {
        return selectedKeys.map(selectionKeysProvider::getPositionForKey)
    }

    fun getFirstSelectedPosition(): Int {
        return getSelectedPositionsUnsorted().minOrNull() ?: -1
    }

    fun isSelected(key: K): Boolean {
        return selectedKeys.contains(key)
    }

    fun toggleSelection(key: K, position: Int) {
        if (key == unselectableKey) {
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
        val keys = selectionKeysProvider.allKeys.filter { it != unselectableKey }
        if (selectedCount == keys.size) {
            return
        }

        _selectedKeys.clear()
        _selectedKeys.addAll(keys)

        adapter.notifyItemRangeChanged(0, selectedKeys.size)

        updateActionMode()
    }

    fun clearSelection(finishActionMode: Boolean = true) {
        if (finishActionMode) {
            actionMode?.finish()
        }
        actionMode = null

        if (selectedKeys.isEmpty()) {
            return
        }

        _selectedKeys.clear()

        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    fun startActionMode() {
        actionMode = activity.startSupportActionMode(actionModeCallbackFactory(this))
        updateActionMode()
    }

    private fun updateActionMode() {
        actionMode?.apply {
            title = context.resources.getQuantityString(titleStringId,
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
            handler.postUpdateActionMode()
        } else {
            actionMode?.finish()
        }
    }

    fun commitAdapterUpdate() = selectionKeysProvider.commitAdapterUpdate()

    fun restoreInstanceState() {
        if (!restoredInstanceState) {
            restoredInstanceState = true
            val bundle = savedStateRegistryOwner.savedStateRegistry.consumeRestoredStateForKey(BUNDLE_KEY)
                    ?: return
            val restored = getSelectedKeysFromBundle(bundle) ?: return
            _selectedKeys.addAll(selectionKeysProvider.allKeys.filter(restored::contains))
            if (hasSelection) {
                startActionMode()
            }
        }
    }

    @Suppress("LeakingThis")
    abstract class ViewHolder<K : Any>(protected val selectionTracker: SelectionTracker<K>,
                                       itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, View.OnLongClickListener {
        private val selectedBackground: View = itemView.findViewById(R.id.selected_background_view)

        init {
            itemView.setOnClickListener {
                if (selectionTracker.hasSelection && getSelectionKey() != selectionTracker.unselectableKey) {
                    selectionTracker.toggleSelection(getSelectionKey(), bindingAdapterPosition)
                } else {
                    onClick(it)
                }
            }
            itemView.setOnLongClickListener(this)
        }

        @CallSuper
        open fun update() {
            selectedBackground.isActivated = selectionTracker.isSelected(getSelectionKey())
        }

        final override fun onLongClick(view: View): Boolean {
            if (selectionTracker.hasSelection || getSelectionKey() == selectionTracker.unselectableKey) {
                return false
            }
            selectionTracker.toggleSelection(getSelectionKey(), bindingAdapterPosition)
            selectionTracker.startActionMode()
            return true
        }

        private fun getSelectionKey(): K {
            return selectionTracker.selectionKeysProvider.getKeyForPosition(bindingAdapterPosition)
        }
    }

    abstract class ActionModeCallback<K : Any>(protected val selectionTracker: SelectionTracker<K>) : ActionMode.Callback {
        protected val activity = selectionTracker.activity

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectionTracker.clearSelection(false)
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == R.id.select_all) {
                selectionTracker.selectAll()
                return true
            }
            return false
        }
    }

    private class SelectionKeysProvider<K : Any>(private val selectionTracker: SelectionTracker<K>,
                                                 private val adapter: RecyclerView.Adapter<*>,
                                                 private val adapterCommitsUpdates: Boolean,
                                                 private val getSelectionKeyForPosition: AdapterSelectionKeyGetter<K>) {
        private val positionToKey = ArrayList<K?>()
        private val keyToPosition = mutableMapOf<K, Int>()

        val allKeys: List<K>
            get() = if (adapterCommitsUpdates) {
                positionToKey.requireNoNulls()
            } else {
                @Suppress("UNCHECKED_CAST")
                positionToKey as List<K>
            }

        private var waitingForCommit = false

        init {
            val observer = object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        if (!adapterCommitsUpdates || ((positionToKey.size + itemCount) == adapter.itemCount)) {
                            positionToKey.addAll(positionStart, List(itemCount) { adapter.getSelectionKeyForPosition(positionStart + it) })
                            for (position in positionStart until (positionStart + itemCount)) {
                                val key = checkNotNull(positionToKey[position])
                                keyToPosition[key] = position
                            }
                        } else {
                            positionToKey.addAll(positionStart, List(itemCount) { null })
                            waitingForCommit = true
                        }
                        for (position in (positionStart + itemCount) until positionToKey.size) {
                            val key = positionToKey[position]
                            if (key != null) {
                                keyToPosition[key] = position
                            }
                        }
                    }
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        for (position in positionStart until (positionStart + itemCount)) {
                            val key = checkNotNull(positionToKey[position])
                            keyToPosition.remove(key)
                            selectionTracker.removeSelectionInProgress(key)
                        }
                        positionToKey.subList(positionStart, positionStart + itemCount).clear()
                        for (position in positionStart until positionToKey.size) {
                            val key = positionToKey[position]
                            if (key != null) {
                                keyToPosition[key] = position
                            }
                        }
                        selectionTracker.finishRemovingSelection()
                    }
                }

                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        val changedPositions = positionToKey.moveItems(fromPosition, toPosition, itemCount)
                        for (position in changedPositions) {
                            val key = positionToKey[position]
                            if (key != null) {
                                keyToPosition[key] = position
                            }
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

        fun commitAdapterUpdate() {
            if (waitingForCommit) {
                val iter = positionToKey.listIterator()
                while (iter.hasNext()) {
                    val position = iter.nextIndex()
                    if (iter.next() == null) {
                        val key = adapter.getSelectionKeyForPosition(position)
                        iter.set(key)
                        keyToPosition[key] = position
                    }
                }
                waitingForCommit = false
            }
        }

        fun getKeyForPosition(position: Int): K {
            return requireNotNull(positionToKey[position])
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

    private class SelectionTrackerHandler(selectionTracker: SelectionTracker<*>) : Handler(Looper.getMainLooper()) {
        private val selectionTrackerWeak = WeakReference(selectionTracker)
        private val msgUpdateActionMode = 0

        override fun handleMessage(msg: Message) {
            if (msg.what == msgUpdateActionMode) {
                selectionTrackerWeak.get()?.updateActionMode()
            }
        }

        fun postUpdateActionMode() {
            if (!hasMessages(msgUpdateActionMode)) {
                sendEmptyMessage(msgUpdateActionMode)
            }
        }
    }
}
