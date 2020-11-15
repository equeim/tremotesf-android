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

package org.equeim.tremotesf.ui

import android.annotation.SuppressLint
import android.os.Build

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager

import org.equeim.tremotesf.Application
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsAdapter


private const val TORRENTS_SORT_MODE = "torrentsSortMode"
private const val TORRENTS_SORT_ORDER = "torrentsSortOrder"
private const val TORRENTS_STATUS_FILTER = "torrentsStatusFilter"
private const val TORRENTS_TRACKER_FILTER = "torrentsTrackerFilter"
private const val TORRENTS_DIRECTORY_FILTER = "torrentsFolderFilter"
private const val DONATE_DIALOG_SHOWN = "donateDialogShown"

@SuppressLint("StaticFieldLeak")
object Settings {
    private val context = Application.instance

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val darkThemeKey = context.getString(R.string.prefs_dark_theme_key)
    val themeKey = context.getString(R.string.prefs_theme_key)
    val oldColorsKey = context.getString(R.string.prefs_old_colors_key)
    private val torrentCompactViewKey = context.getString(R.string.prefs_torrent_compact_view_key)
    private val torrentNameMultilineKey = context.getString(R.string.prefs_torrent_name_multiline_key)
    val persistentNotificationKey = context.getString(R.string.prefs_persistent_notification_key)
    val notifyOnFinishedKey = context.getString(R.string.prefs_notify_on_finished_key)
    val notifyOnAddedKey = context.getString(R.string.prefs_notify_on_added_key)
    val backgroundUpdateIntervalKey = context.getString(R.string.prefs_background_update_interval_key)
    private val notifyOnFinishedSinceLastConnectionKey = context.getString(R.string.prefs_notify_on_finished_since_last_key)
    private val notifyOnAddedSinceLastConnectionKey = context.getString(R.string.prefs_notify_on_added_since_last_key)
    private val deleteFilesKey = context.getString(R.string.prefs_delete_files_key)

    private const val THEME_AUTO = "auto"
    private const val THEME_DARK = "dark"
    private const val THEME_LIGHT = "light"

    init {
        if (!preferences.contains(themeKey) && preferences.contains(darkThemeKey)) {
            preferences.edit {
                putString(themeKey, if (preferences.getBoolean(darkThemeKey, false)) {
                    THEME_DARK
                } else {
                    THEME_LIGHT
                })
            }
        }
    }

    val nightMode: Int
        get() {
            return when (preferences.getString(themeKey, THEME_AUTO)) {
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            }
        }

    private val oldColors: Boolean
        get() {
            return preferences.getBoolean(oldColorsKey, false)
        }

    val theme: Int
        get() {
            return if (oldColors) R.style.AppTheme_Old else R.style.AppTheme
        }

    val torrentCompactView: Boolean
        get() {
            return preferences.getBoolean(torrentCompactViewKey, false)
        }

    val torrentNameMultiline: Boolean
        get() {
            return preferences.getBoolean(torrentNameMultilineKey, false)
        }

    val showPersistentNotification: Boolean
        get() {
            return preferences.getBoolean(persistentNotificationKey, false)
        }

    val notifyOnFinished: Boolean
        get() {
            return preferences.getBoolean(notifyOnFinishedKey, true)
        }

    val notifyOnAdded: Boolean
        get() {
            return preferences.getBoolean(notifyOnAddedKey, false)
        }

    val backgroundUpdateInterval: Long
        get() {
            return try {
                preferences.getString(backgroundUpdateIntervalKey, "0")?.toLong() ?: 0
            } catch (ignore: NumberFormatException) {
                0
            }
        }

    val notifyOnFinishedSinceLastConnection: Boolean
        get() {
            return preferences.getBoolean(notifyOnFinishedSinceLastConnectionKey, false)
        }

    val notifyOnAddedSinceLastConnection: Boolean
        get() {
            return preferences.getBoolean(notifyOnAddedSinceLastConnectionKey, false)
        }

    val deleteFiles: Boolean
        get() {
            return preferences.getBoolean(deleteFilesKey, false)
        }

    var torrentsSortMode: TorrentsAdapter.SortMode
        get() {
            val int = preferences.getInt(TORRENTS_SORT_MODE, 0)
            if (int in TorrentsAdapter.SortMode.values().indices) {
                return TorrentsAdapter.SortMode.values()[int]
            }
            return TorrentsAdapter.SortMode.Name
        }
        set(value) {
            preferences.edit { putInt(TORRENTS_SORT_MODE, value.ordinal) }
        }

    var torrentsSortOrder: TorrentsAdapter.SortOrder
        get() {
            val int = preferences.getInt(TORRENTS_SORT_ORDER, 0)
            if (int in TorrentsAdapter.SortOrder.values().indices) {
                return TorrentsAdapter.SortOrder.values()[int]
            }
            return TorrentsAdapter.SortOrder.Ascending
        }
        set(value) {
            preferences.edit { putInt(TORRENTS_SORT_ORDER, value.ordinal) }
        }

    var torrentsStatusFilter: TorrentsAdapter.StatusFilterMode
        get() {
            val int = preferences.getInt(TORRENTS_STATUS_FILTER, 0)
            if (int in TorrentsAdapter.StatusFilterMode.values().indices) {
                return TorrentsAdapter.StatusFilterMode.values()[int]
            }
            return TorrentsAdapter.StatusFilterMode.All
        }
        set(value) {
            preferences.edit { putInt(TORRENTS_STATUS_FILTER, value.ordinal) }
        }

    var torrentsTrackerFilter: String
        get() {
            return preferences.getString(TORRENTS_TRACKER_FILTER, "") ?: ""
        }
        set(value) {
            preferences.edit { putString(TORRENTS_TRACKER_FILTER, value) }
        }

    var torrentsDirectoryFilter: String
        get() {
            return preferences.getString(TORRENTS_DIRECTORY_FILTER, "") ?: ""
        }
        set(value) {
            preferences.edit { putString(TORRENTS_DIRECTORY_FILTER, value) }
        }

    var donateDialogShown: Boolean
        get() {
            return preferences.getBoolean(DONATE_DIALOG_SHOWN, false)
        }
        set(value) {
            preferences.edit { putBoolean(DONATE_DIALOG_SHOWN, value) }
        }
}