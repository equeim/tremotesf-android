// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
