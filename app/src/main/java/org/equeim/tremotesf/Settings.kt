/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

import android.preference.PreferenceManager


object Settings : SharedPreferences.OnSharedPreferenceChangeListener {
    private var initialized = false
    private lateinit var context: Context

    private lateinit var preferences: SharedPreferences

    private lateinit var darkThemeKey: String
    private lateinit var backgroundServiceKey: String
    private lateinit var persistentNotificationKey: String

    private val darkTheme: Boolean
        get() {
            return preferences.getBoolean(darkThemeKey, true)
        }

    val theme: Int
        get() {
            return if (darkTheme) {
                R.style.AppTheme_Dark
            } else {
                R.style.AppTheme_Light
            }
        }

    val themeNoActionBar: Int
        get() {
            return if (darkTheme) {
                R.style.AppTheme_Dark_NoActionBar
            } else {
                R.style.AppTheme_Light_NoActionBar
            }
        }

    val backgroundServiceEnabled: Boolean
        get() {
            return preferences.getBoolean(backgroundServiceKey, false)
        }

    val showPersistentNotification: Boolean
        get() {
            return preferences.getBoolean(persistentNotificationKey, false)
        }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
        when (key) {
            backgroundServiceKey -> {
                if (backgroundServiceEnabled) {
                    context.startService(Intent(context, BackgroundService::class.java))
                } else {
                    context.stopService(Intent(context, BackgroundService::class.java))
                }
            }
            persistentNotificationKey -> {
                if (BackgroundService.instance != null) {
                    if (showPersistentNotification) {
                        BackgroundService.instance!!.startForeground()
                    } else {
                        BackgroundService.instance!!.stopForeground()
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (initialized) {
            return
        }
        initialized = true

        this.context = context

        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)

        darkThemeKey = context.getString(R.string.prefs_dark_theme_key)
        backgroundServiceKey = context.getString(R.string.prefs_background_service_key)
        persistentNotificationKey = context.getString(R.string.prefs_persistent_notification_key)
    }
}