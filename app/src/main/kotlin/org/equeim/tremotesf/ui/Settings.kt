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
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.common.enumFromInt
import org.equeim.tremotesf.ui.Settings.Property
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import kotlin.reflect.KClass


private const val TORRENTS_SORT_MODE = "torrentsSortMode"
private const val TORRENTS_SORT_ORDER = "torrentsSortOrder"
private const val TORRENTS_STATUS_FILTER = "torrentsStatusFilter"
private const val TORRENTS_TRACKER_FILTER = "torrentsTrackerFilter"
private const val TORRENTS_DIRECTORY_FILTER = "torrentsFolderFilter"

@SuppressLint("StaticFieldLeak")
object Settings {
    private val context: Context = TremotesfApplication.instance
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val darkThemeKey = context.getString(R.string.prefs_dark_theme_key)
    private val themeKey = context.getString(R.string.prefs_theme_key)

    val persistentNotificationKey = context.getString(R.string.prefs_persistent_notification_key)
    val notifyOnFinishedKey = context.getString(R.string.prefs_notify_on_finished_key)
    val notifyOnAddedKey = context.getString(R.string.prefs_notify_on_added_key)
    val backgroundUpdateIntervalKey =
        context.getString(R.string.prefs_background_update_interval_key)

    private const val THEME_AUTO = "auto"
    private const val THEME_DARK = "dark"
    private const val THEME_LIGHT = "light"

    @Volatile
    private var migrated = false
    private val migrationMutex = Mutex()

    private suspend fun migrate() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            withContext(Dispatchers.IO) {
                if (!preferences.contains(themeKey) && preferences.contains(darkThemeKey)) {
                    preferences.edit {
                        putString(
                            themeKey, if (preferences.getBoolean(darkThemeKey, false)) {
                                THEME_DARK
                            } else {
                                THEME_LIGHT
                            }
                        )
                    }
                }
                migrated = true
            }
        }
    }

    val nightMode: Property<Int> = property(themeKey, THEME_AUTO).map {
        when (it) {
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else {
                AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            }
        }
    }

    val theme: Property<Int> = property(R.string.prefs_old_colors_key, false)
        .map { if (it) R.style.AppTheme_Teal else R.style.AppTheme }

    val torrentCompactView: Property<Boolean> = property(R.string.prefs_torrent_compact_view_key, false)

    val torrentNameMultiline: Property<Boolean> = property(R.string.prefs_torrent_name_multiline_key, false)

    val quickReturn: Property<Boolean> = property(R.string.prefs_quick_return, false)

    val showPersistentNotification: Property<Boolean> = property(persistentNotificationKey, false)

    val notifyOnFinished: Property<Boolean> = property(notifyOnFinishedKey, true)

    val notifyOnAdded: Property<Boolean> = property(notifyOnAddedKey, false)

    val backgroundUpdateInterval: Property<Long> = property(backgroundUpdateIntervalKey, "0").map {
        try {
            it.toLong()
        } catch (ignore: NumberFormatException) {
            0
        }
    }

    val notifyOnFinishedSinceLastConnection: Property<Boolean> =
        property(R.string.prefs_notify_on_finished_since_last_key, false)

    val notifyOnAddedSinceLastConnection: Property<Boolean> =
        property(R.string.prefs_notify_on_added_since_last_key, false)

    val deleteFiles: Property<Boolean> = property(R.string.prefs_delete_files_key, false)

    val torrentsSortMode: MutableProperty<TorrentsListFragmentViewModel.SortMode> =
        mutableProperty(TORRENTS_SORT_MODE, -1).map(
            transformGetter = { enumFromInt(it, TorrentsListFragmentViewModel.SortMode.DEFAULT) },
            transformSetter = { it.ordinal }
        )

    val torrentsSortOrder: MutableProperty<TorrentsListFragmentViewModel.SortOrder> =
        mutableProperty(TORRENTS_SORT_ORDER, -1).map(
            transformGetter = { enumFromInt(it, TorrentsListFragmentViewModel.SortOrder.DEFAULT) },
            transformSetter = { it.ordinal }
        )

    val torrentsStatusFilter: MutableProperty<TorrentsListFragmentViewModel.StatusFilterMode> =
        mutableProperty(TORRENTS_STATUS_FILTER, -1).map(
            transformGetter = { enumFromInt(it, TorrentsListFragmentViewModel.StatusFilterMode.DEFAULT) },
            transformSetter = { it.ordinal }
        )

    val torrentsTrackerFilter: MutableProperty<String> = mutableProperty(TORRENTS_TRACKER_FILTER, "")

    var torrentsDirectoryFilter: MutableProperty<String> = mutableProperty(TORRENTS_DIRECTORY_FILTER, "")

    private inline fun <reified T : Any> property(
        key: String,
        defaultValue: T
    ): Property<T> {
        val getter = getSharedPreferencesGetter(T::class)
        return object : Property<T> {
            override suspend fun get(): T {
                migrate()
                return preferences.getter(key, defaultValue)
            }

            override fun flow() = callbackFlow {
                send(get())
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                    if (changedKey == key) {
                        launch { send(get()) }
                    }
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }.flowOn(Dispatchers.IO)
        }
    }

    private inline fun <reified T : Any> property(
        @StringRes keyResId: Int,
        defaultValue: T
    ) = property(context.getString(keyResId), defaultValue)

    private inline fun <reified T : Any> mutableProperty(
        key: String,
        defaultValue: T
    ): MutableProperty<T> {
        val property = property(key, defaultValue)
        val setter = getSharedPreferencesSetter(T::class)
        return object : MutableProperty<T>, Property<T> by property {
            override suspend fun set(value: T) = withContext(Dispatchers.IO) {
                migrate()
                preferences.setter(key, value)
            }
        }
    }

    interface Property<T : Any> {
        suspend fun get(): T
        fun flow(): Flow<T>
    }

    interface MutableProperty<T : Any> : Property<T> {
        suspend fun set(value: T)
    }
}

private fun <T : Any> getSharedPreferencesGetter(kClass: KClass<T>): suspend SharedPreferences.(String, T) -> T {
    @Suppress("UNCHECKED_CAST")
    val baseGetter = when (kClass) {
        Boolean::class -> SharedPreferences::getBoolean
        Float::class -> SharedPreferences::getFloat
        Int::class -> SharedPreferences::getInt
        Long::class -> SharedPreferences::getLong
        String::class -> SharedPreferences::getString
        Set::class -> SharedPreferences::getStringSet
        else -> throw IllegalArgumentException("Unsupported property type $kClass")
    } as SharedPreferences.(String, T) -> T
    return { key, defaultValue ->
        withContext(Dispatchers.IO) {
            baseGetter(key, defaultValue)
        }
    }
}

private fun <T : Any> getSharedPreferencesSetter(kClass: KClass<T>): suspend SharedPreferences.(String, T) -> Unit {
    @Suppress("UNCHECKED_CAST")
    val baseSetter = when (kClass) {
        Boolean::class -> SharedPreferences.Editor::putBoolean
        Float::class -> SharedPreferences.Editor::putFloat
        Int::class -> SharedPreferences.Editor::putInt
        Long::class -> SharedPreferences.Editor::putLong
        String::class -> SharedPreferences.Editor::putString
        Set::class -> SharedPreferences.Editor::putStringSet
        else -> throw IllegalArgumentException("Unsupported property type $kClass")
    } as SharedPreferences.Editor.(String, T) -> Unit
    return { key, value ->
        withContext(Dispatchers.IO) {
            edit {
                baseSetter(key, value)
            }
        }
    }
}

private fun <T : Any, R : Any> Property<T>.map(transform: suspend (T) -> R) =
    object : Property<R> {
        override suspend fun get() = transform(this@map.get())
        override fun flow() = this@map.flow().map(transform)
    }

private fun <T : Any, R : Any> Settings.MutableProperty<T>.map(
    transformGetter: suspend (T) -> R,
    transformSetter: suspend (R) -> T
) =
    object : Settings.MutableProperty<R> {
        override suspend fun get() = transformGetter(this@map.get())
        override fun flow(): Flow<R> = this@map.flow().map(transformGetter)
        override suspend fun set(value: R) = this@map.set(transformSetter(value))
    }
