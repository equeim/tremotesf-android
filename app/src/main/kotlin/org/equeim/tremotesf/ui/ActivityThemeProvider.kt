package org.equeim.tremotesf.ui

import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ActivityThemeProvider {
    val theme: StateFlow<Int>

    /**
     * Get initial values of theme and night mode, blocking main thread
     * until they are retrieved from SharedPreferences
     */
    init {
        Timber.i("init() called")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        theme = Settings.theme.flow().stateIn(scope, SharingStarted.Eagerly, Int.MIN_VALUE)

        val initialNightModeContinuation = AtomicReference<Continuation<Int>>()
        val nightMode = Settings.nightMode.flow().onEach {
            when (val continuation = initialNightModeContinuation.getAndSet(null)) {
                // Can't use withContext(Dispatchers.Main) when we are blocking main thread in runBlocking,
                // resume coroutine instead
                null -> {
                    Timber.i("Night mode changed")
                    withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(it) }
                }
                else -> continuation.resume(it)
            }
        }

        runBlocking {
            launch {
                theme.first { it != Int.MIN_VALUE }
                Timber.i("Received initial value of theme")
            }

            launch {
                val initialNightMode = suspendCoroutine<Int> {
                    initialNightModeContinuation.set(it)
                    nightMode.launchIn(scope)
                }
                Timber.i("Received initial value of nightMode")
                AppCompatDelegate.setDefaultNightMode(initialNightMode)
            }
        }

        Timber.i("init() returned")
    }
}
