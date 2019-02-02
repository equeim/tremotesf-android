/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

import androidx.core.content.edit

import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.startService

import org.equeim.tremotesf.mainactivity.TorrentsAdapter


private const val TORRENTS_SORT_MODE = "torrentsSortMode"
private const val TORRENTS_SORT_ORDER = "torrentsSortOrder"
private const val TORRENTS_STATUS_FILTER = "torrentsStatusFilter"
private const val TORRENTS_TRACKER_FILTER = "torrentsTrackerFilter"
private const val TORRENTS_DIRECTORY_FILTER = "torrentsFolderFilter"
private const val DONATE_DIALOG_SHOWN = "donateDialogShown"

@SuppressLint("StaticFieldLeak")
object Settings : SharedPreferences.OnSharedPreferenceChangeListener {
    var context: Context? = null
        set(value) {
            field = value
            if (value == null) {
                preferences = null
            } else {
                preferences = value.defaultSharedPreferences
                preferences!!.registerOnSharedPreferenceChangeListener(this)

                darkThemeKey = value.getString(R.string.prefs_dark_theme_key)
                oldColorsKey = value.getString(R.string.prefs_old_colors_key)
                torrentCompactViewKey = value.getString(R.string.prefs_torrent_compact_view_key)
                torrentNameMultilineKey = value.getString(R.string.prefs_torrent_name_multiline_key)
                backgroundServiceKey = value.getString(R.string.prefs_background_service_key)
                persistentNotificationKey = value.getString(R.string.prefs_persistent_notification_key)
                notifyOnFinishedKey = value.getString(R.string.prefs_notify_on_finished_key)
                notifyOnAddedKey = value.getString(R.string.prefs_notify_on_added_key)
                notifyOnFinishedSinceLastConnectionKey = value.getString(R.string.prefs_notify_on_finished_since_last_key)
                notifyOnAddedSinceLastConnectionKey = value.getString(R.string.prefs_notify_on_added_since_last_key)
                deleteFilesKey = value.getString(R.string.prefs_delete_files_key)
            }
        }

    private var preferences: SharedPreferences? = null

    private lateinit var darkThemeKey: String
    private lateinit var oldColorsKey: String
    private lateinit var torrentCompactViewKey: String
    private lateinit var torrentNameMultilineKey: String
    private lateinit var backgroundServiceKey: String
    private lateinit var persistentNotificationKey: String
    private lateinit var notifyOnFinishedKey: String
    private lateinit var notifyOnAddedKey: String
    private lateinit var notifyOnFinishedSinceLastConnectionKey: String
    private lateinit var notifyOnAddedSinceLastConnectionKey: String
    private lateinit var deleteFilesKey: String

    private val darkTheme: Boolean
        get() {
            return preferences!!.getBoolean(darkThemeKey, true)
        }

    private val oldColors: Boolean
        get() {
            return preferences!!.getBoolean(oldColorsKey, false)
        }

    val theme: Int
        get() {
            val old = oldColors
            return if (darkTheme) {
                if (old) R.style.AppTheme_Dark_Old else R.style.AppTheme_Dark
            } else {
                if (old) R.style.AppTheme_Light_Old else R.style.AppTheme_Light
            }
        }

    val themeNoActionBar: Int
        get() {
            val old = oldColors
            return if (darkTheme) {
                if (old) R.style.AppTheme_Dark_Old_NoActionBar else R.style.AppTheme_Dark_NoActionBar
            } else {
                if (old) R.style.AppTheme_Light_Old_NoActionBar else R.style.AppTheme_Light_NoActionBar
            }
        }

    val torrentCompactView: Boolean
        get() {
            return preferences!!.getBoolean(torrentCompactViewKey, false)
        }

    var torrentCompactViewListener: (() -> Unit)? = null

    val torrentNameMultiline: Boolean
        get() {
            return preferences!!.getBoolean(torrentNameMultilineKey, false)
        }

    var torrentNameMultilineListener: (() -> Unit)? = null

    val backgroundServiceEnabled: Boolean
        get() {
            return preferences!!.getBoolean(backgroundServiceKey, false)
        }

    val showPersistentNotification: Boolean
        get() {
            return preferences!!.getBoolean(persistentNotificationKey, false)
        }

    val notifyOnFinished: Boolean
        get() {
            return preferences!!.getBoolean(notifyOnFinishedKey, true)
        }

    val notifyOnAdded: Boolean
        get() {
            return preferences!!.getBoolean(notifyOnAddedKey, false)
        }

    val notifyOnFinishedSinceLastConnection: Boolean
        get() {
            return preferences!!.getBoolean(notifyOnFinishedSinceLastConnectionKey, false)
        }

    val notifyOnAddedSinceLastConnection: Boolean
        get() {
            return preferences!!.getBoolean(notifyOnAddedSinceLastConnectionKey, false)
        }

    val deleteFiles: Boolean
        get() {
            return preferences!!.getBoolean(deleteFilesKey, false)
        }

    var torrentsSortMode: TorrentsAdapter.SortMode
        get() {
            val int = preferences!!.getInt(TORRENTS_SORT_MODE, 0)
            if (int in TorrentsAdapter.SortMode.values().indices) {
                return TorrentsAdapter.SortMode.values()[int]
            }
            return TorrentsAdapter.SortMode.Name
        }
        set(value) {
            preferences!!.edit { putInt(TORRENTS_SORT_MODE, value.ordinal) }
        }

    var torrentsSortOrder: TorrentsAdapter.SortOrder
        get() {
            val int = preferences!!.getInt(TORRENTS_SORT_ORDER, 0)
            if (int in TorrentsAdapter.SortOrder.values().indices) {
                return TorrentsAdapter.SortOrder.values()[int]
            }
            return TorrentsAdapter.SortOrder.Ascending
        }
        set(value) {
            preferences!!.edit { putInt(TORRENTS_SORT_ORDER, value.ordinal) }
        }

    var torrentsStatusFilter: TorrentsAdapter.StatusFilterMode
        get() {
            val int = preferences!!.getInt(TORRENTS_STATUS_FILTER, 0)
            if (int in TorrentsAdapter.StatusFilterMode.values().indices) {
                return TorrentsAdapter.StatusFilterMode.values()[int]
            }
            return TorrentsAdapter.StatusFilterMode.All
        }
        set(value) {
            preferences!!.edit { putInt(TORRENTS_STATUS_FILTER, value.ordinal) }
        }

    var torrentsTrackerFilter: String
        get() {
            return preferences!!.getString(TORRENTS_TRACKER_FILTER, "")!!
        }
        set(value) {
            preferences!!.edit { putString(TORRENTS_TRACKER_FILTER, value) }
        }

    var torrentsDirectoryFilter: String
        get() {
            return preferences!!.getString(TORRENTS_DIRECTORY_FILTER, "")!!
        }
        set(value) {
            preferences!!.edit { putString(TORRENTS_DIRECTORY_FILTER, value) }
        }

    var donateDialogShown: Boolean
        get() {
            return preferences!!.getBoolean(DONATE_DIALOG_SHOWN, false)
        }
        set(value) {
            preferences!!.edit { putBoolean(DONATE_DIALOG_SHOWN, value) }
        }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String) {
        when (key) {
            torrentCompactViewKey -> torrentCompactViewListener?.invoke()
            torrentNameMultilineKey -> torrentNameMultilineListener?.invoke()
            backgroundServiceKey -> {
                if (backgroundServiceEnabled) {
                    context!!.startService<BackgroundService>()
                } else {
                    BackgroundService.instance?.stopService()
                }
            }
            persistentNotificationKey -> {
                if (BackgroundService.instance != null) {
                    if (showPersistentNotification) {
                        BackgroundService.instance?.startForeground()
                    } else {
                        BackgroundService.instance?.stopForeground()
                    }
                }
            }
        }
    }
}