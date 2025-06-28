// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.UiModeManager
import android.os.Build
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.equeim.tremotesf.TremotesfApplication
import timber.log.Timber

object ActivityThemeProvider {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val colorTheme: StateFlow<Settings.ColorTheme>
    val darkThemeMode: StateFlow<Settings.DarkThemeMode>

    /**
     * Get initial values of theme and night mode, blocking main thread
     * until they are retrieved from SharedPreferences
     */
    init {
        Timber.i("init() called")

        val (initialColorTheme, initialDarkThemeMode) = runBlocking {
            val colors =
                async { Settings.colorTheme.get().also { Timber.i("Received initial value of color theme: $it") } }
            val darkThemeMode = async {
                Settings.darkThemeMode.get().also { Timber.i("Received initial value of dark theme mode: $it") }
            }
            colors.await() to darkThemeMode.await()
        }

        colorTheme = Settings.colorTheme.flow()
            .stateIn(coroutineScope, SharingStarted.Eagerly, initialColorTheme)
        darkThemeMode = Settings.darkThemeMode.flow()
            .stateIn(coroutineScope, SharingStarted.Eagerly, initialDarkThemeMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = checkNotNull(TremotesfApplication.instance.getSystemService<UiModeManager>())
            darkThemeMode.dropWhile { it == initialDarkThemeMode }.onEach {
                Timber.i("Dark theme mode changed to $it, set night mode")
                uiModeManager.setApplicationNightMode(it.nightMode)
            }.launchIn(coroutineScope)
        }

        Timber.i("init() returned")
    }

    private val Settings.DarkThemeMode.nightMode: Int
        get() = when (this) {
            Settings.DarkThemeMode.Auto -> UiModeManager.MODE_NIGHT_AUTO
            Settings.DarkThemeMode.On -> UiModeManager.MODE_NIGHT_YES
            Settings.DarkThemeMode.Off -> UiModeManager.MODE_NIGHT_NO
        }
}
