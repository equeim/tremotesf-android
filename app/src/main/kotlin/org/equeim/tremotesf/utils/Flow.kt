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

package org.equeim.tremotesf.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

@Suppress("FunctionName")
fun <T> MutableEventFlow(): MutableSharedFlow<T> {
    return MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

fun <T> Flow<T>.collectWhenStarted(lifecycleOwner: LifecycleOwner)
        = lifecycleOwner.lifecycleScope.launchWhenStarted { collect() }

inline fun <T> Flow<T>.collectWhenStarted(lifecycleOwner: LifecycleOwner, crossinline action: suspend (value: T) -> Unit)
    = lifecycleOwner.lifecycleScope.launchWhenStarted { collect(action) }

inline fun MutableStateFlow<Boolean>.handleAndReset(crossinline action: suspend () -> Unit)
    = filter { it }.onEach {
    action()
    compareAndSet(it, false)
}