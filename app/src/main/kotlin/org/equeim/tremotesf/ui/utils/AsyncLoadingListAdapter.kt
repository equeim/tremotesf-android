/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Restores state when non-null list is passed to [submitList]
 */
abstract class AsyncLoadingListAdapter<T : Any?, VH : RecyclerView.ViewHolder>(diffCallback: DiffUtil.ItemCallback<T>) :
    ListAdapter<T, VH>(diffCallback) {

    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT
    }

    override fun submitList(list: List<T>?) = submitList(list, null)

    override fun submitList(list: List<T>?, commitCallback: Runnable?) =
        super.submitList(list?.nullIfEmpty()) {
            if (list != null && stateRestorationPolicy == StateRestorationPolicy.PREVENT) {
                stateRestorationPolicy = StateRestorationPolicy.ALLOW
                onStateRestored()
            }
            commitCallback?.run()
        }

    protected open fun onStateRestored() {}
}

suspend fun <T> ListAdapter<T, *>.submitListAwait(list: List<T>?): Unit =
    suspendCancellableCoroutine { continuation ->
        submitList(list) { continuation.resume(Unit) }
    }

fun <T> List<T>.nullIfEmpty(): List<T>? = ifEmpty { null }
