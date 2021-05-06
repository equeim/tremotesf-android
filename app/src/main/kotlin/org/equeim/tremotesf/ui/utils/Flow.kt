package org.equeim.tremotesf.ui.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

fun <T> Flow<T>.collectWhenStarted(lifecycleOwner: LifecycleOwner) =
    lifecycleOwner.lifecycleScope.launchWhenStarted { collect() }

inline fun <T> Flow<T>.collectWhenStarted(
    lifecycleOwner: LifecycleOwner,
    crossinline action: suspend (value: T) -> Unit
) = lifecycleOwner.lifecycleScope.launchWhenStarted { collect(action) }

inline fun MutableStateFlow<Boolean>.handleAndReset(crossinline action: suspend () -> Unit) =
    filter { it }.onEach {
        action()
        compareAndSet(it, false)
    }
