// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object ActivityThemeProvider {
    val colorTheme: StateFlow<Settings.ColorTheme>

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

        val scope = MainScope()

        colorTheme = Settings.colorTheme.flow()
            .stateIn(scope, SharingStarted.Eagerly, initialColorTheme)

        AppCompatDelegate.setDefaultNightMode(initialDarkThemeMode.nightMode)
        Settings.darkThemeMode.flow().dropWhile { it == initialDarkThemeMode }.onEach {
            Timber.i("Dark theme mode changed to $it")
            AppCompatDelegate.setDefaultNightMode(it.nightMode)
        }.launchIn(scope)

        Timber.i("init() returned")
    }
}
