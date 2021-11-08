package org.equeim.tremotesf.ui.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun <T> Flow<T>.launchAndCollectWhenStarted(lifecycleOwner: LifecycleOwner) =
    lifecycleOwner.lifecycleScope.launch { lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect() } }

inline fun <T> Flow<T>.launchAndCollectWhenStarted(
    lifecycleOwner: LifecycleOwner,
    crossinline action: suspend (value: T) -> Unit
) = lifecycleOwner.lifecycleScope.launch { lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect(action) } }

inline fun MutableStateFlow<Boolean>.handleAndReset(crossinline action: suspend () -> Unit) =
    filter { it }.onEach {
        action()
        compareAndSet(it, false)
    }
