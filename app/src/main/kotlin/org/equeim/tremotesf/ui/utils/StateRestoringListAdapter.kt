/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.utils

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

abstract class StateRestoringListAdapter<T : Any?, VH : RecyclerView.ViewHolder>(diffCallback: DiffUtil.ItemCallback<T>) :
    ListAdapter<T, VH>(diffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT
    }

    abstract fun allowStateRestoring(): Boolean

    override fun submitList(list: List<T>?) = submitList(list, null)

    override fun submitList(list: List<T>?, commitCallback: Runnable?) {
        val restore = allowStateRestoring()
        super.submitList(list?.nullIfEmpty()) {
            commitCallback?.run()
            if (restore && stateRestorationPolicy == StateRestorationPolicy.PREVENT) {
                Timber.i("commitCallback: restoring state")
                stateRestorationPolicy = StateRestorationPolicy.ALLOW
                onStateRestored()
            }
        }
    }

    protected open fun onStateRestored() {}

    private companion object {
        fun <T> List<T>.nullIfEmpty() = if (isEmpty()) null else this
    }
}
