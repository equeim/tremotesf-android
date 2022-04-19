/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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
import androidx.annotation.AnyRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.common.enumFromInt
import org.equeim.tremotesf.ui.Settings.Property
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import timber.log.Timber
import kotlin.reflect.KClass


@SuppressLint("StaticFieldLeak")
object Settings {
    private val context: Context = TremotesfApplication.instance
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Volatile
    private var migrated = false
    private val migrationMutex = Mutex()

    private suspend fun migrate() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            withContext(Dispatchers.IO) {
                context.getString(R.string.deprecated_prefs_dark_theme_key)
                    .let { deprecatedDarkThemeKey ->
                        if (preferences.contains(deprecatedDarkThemeKey)) {
                            preferences.edit {
                                putString(
                                    darkThemeMode.key,
                                    if (preferences.getBoolean(deprecatedDarkThemeKey, false)) {
                                        DarkThemeMode.On.prefsValue
                                    } else {
                                        DarkThemeMode.Off.prefsValue
                                    }
                                )
                                remove(deprecatedDarkThemeKey)
                            }
                        }
                    }
                context.getString(R.string.deprecated_prefs_old_colors_key)
                    .let { deprecatedOldColorsKey ->
                        if (preferences.contains(deprecatedOldColorsKey)) {
                            preferences.edit {
                                putString(
                                    colorTheme.key,
                                    if (preferences.getBoolean(deprecatedOldColorsKey, false)) {
                                        ColorTheme.Teal.prefsValue
                                    } else {
                                        ColorTheme.Red.prefsValue
                                    }
                                )
                                remove(deprecatedOldColorsKey)
                            }
                        }
                    }
                context.getString(R.string.deprecated_prefs_theme_key).let { deprecatedThemeKey ->
                    if (preferences.contains(deprecatedThemeKey)) {
                        preferences.edit {
                            val darkThemeModeValue =
                                when (preferences.getString(deprecatedThemeKey, null)) {
                                    context.getString(R.string.deprecated_prefs_theme_value_auto) -> DarkThemeMode.Auto
                                    context.getString(R.string.deprecated_prefs_theme_value_dark) -> DarkThemeMode.On
                                    context.getString(R.string.deprecated_prefs_theme_value_light) -> DarkThemeMode.Off
                                    else -> null
                                }
                            darkThemeModeValue?.let { putString(darkThemeMode.key, it.prefsValue) }
                            remove(deprecatedThemeKey)
                        }
                    }
                }
                if (
                    (preferences.getString(colorTheme.key, null) ==
                            context.getString(R.string.prefs_color_theme_system)) &&
                    !DynamicColors.isDynamicColorAvailable()
                ) {
                    val newValue =
                        context.getString(R.string.prefs_color_theme_default_value)
                    Timber.e("Dynamic colors are not supported, setting ${colorTheme.key} value to $newValue")
                    preferences.edit {
                        putString(colorTheme.key, newValue)
                    }
                }
                migrated = true
            }
        }
    }

    private interface MappedPrefsEnum {
        val prefsValue: String

        companion object {
            inline fun <reified T> fromPrefsValueProvider(
                @StringRes keyResId: Int,
                @StringRes defaultValueResId: Int
            ): (String) -> T where T : MappedPrefsEnum, T : Enum<T> {
                val values = enumValues<T>()
                return { prefsValue ->
                    values.find { it.prefsValue == prefsValue } ?: run {
                        Timber.e("Unknown value $prefsValue for key ${context.getString(keyResId)}")
                        val defaultPrefsValue = context.getString(defaultValueResId)
                        values.single { it.prefsValue == defaultPrefsValue }
                    }
                }
            }
        }
    }

    enum class ColorTheme(
        @StringRes prefsValueResId: Int,
        @StyleRes val activityThemeResId: Int = 0
    ) : MappedPrefsEnum {
        System(R.string.prefs_color_theme_value_system),
        Red(R.string.prefs_color_theme_value_red, R.style.AppTheme),
        Teal(R.string.prefs_color_theme_value_teal, R.style.AppTheme_Teal);

        override val prefsValue = context.getString(prefsValueResId)

        companion object {
            val fromPrefsValue = MappedPrefsEnum.fromPrefsValueProvider<ColorTheme>(
                R.string.prefs_color_theme_key,
                R.string.prefs_color_theme_default_value
            )
        }
    }

    val colorTheme: MutableProperty<ColorTheme> = mutableProperty<String>(
        R.string.prefs_color_theme_key,
        R.string.prefs_color_theme_default_value
    ).map(
        transformGetter = ColorTheme.fromPrefsValue,
        transformSetter = { it.prefsValue }
    )

    enum class DarkThemeMode(@StringRes prefsValueResId: Int, val nightMode: Int) :
        MappedPrefsEnum {
        Auto(
            R.string.prefs_dark_theme_mode_value_auto,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else {
                AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            }
        ),
        On(R.string.prefs_dark_theme_mode_value_on, AppCompatDelegate.MODE_NIGHT_YES),
        Off(R.string.prefs_dark_theme_mode_value_off, AppCompatDelegate.MODE_NIGHT_NO);

        override val prefsValue = context.getString(prefsValueResId)

        companion object {
            val fromPrefsValue = MappedPrefsEnum.fromPrefsValueProvider<DarkThemeMode>(
                R.string.prefs_dark_theme_mode_key,
                R.string.prefs_dark_theme_mode_default_value
            )
        }
    }

    val darkThemeMode: Property<DarkThemeMode> =
        property<String>(
            R.string.prefs_dark_theme_mode_key,
            R.string.prefs_dark_theme_mode_default_value
        ).map(DarkThemeMode.fromPrefsValue)

    val torrentCompactView: Property<Boolean> = property(
        R.string.prefs_torrent_compact_view_key,
        R.bool.prefs_torrent_compact_view_default_value
    )

    val torrentNameMultiline: Property<Boolean> = property(
        R.string.prefs_torrent_name_multiline_key,
        R.bool.prefs_torrent_name_multiline_default_value
    )

    val quickReturn: Property<Boolean> =
        property(R.string.prefs_quick_return, R.bool.prefs_quick_return_default_value)

    val showPersistentNotification: Property<Boolean> = property(
        R.string.prefs_persistent_notification_key,
        R.bool.prefs_persistent_notification_default_value
    )

    val notifyOnFinished: Property<Boolean> = property(
        R.string.prefs_notify_on_finished_key,
        R.bool.prefs_notify_on_finished_default_value
    )

    val notifyOnAdded: Property<Boolean> =
        property(R.string.prefs_notify_on_added_key, R.bool.prefs_notify_on_added_default_value)

    val backgroundUpdateInterval: Property<Long> = property<String>(
        R.string.prefs_background_update_interval_key,
        R.string.prefs_background_update_interval_default_value
    ).map {
        try {
            it.toLong()
        } catch (ignore: NumberFormatException) {
            0
        }
    }

    val notifyOnFinishedSinceLastConnection: Property<Boolean> =
        property(
            R.string.prefs_notify_on_finished_since_last_key,
            R.bool.prefs_notify_on_finished_since_last_default_value
        )

    val notifyOnAddedSinceLastConnection: Property<Boolean> =
        property(
            R.string.prefs_notify_on_added_since_last_key,
            R.bool.prefs_notify_on_added_since_last_default_value
        )

    val deleteFiles: Property<Boolean> =
        property(R.string.prefs_delete_files_key, R.bool.prefs_delete_files_default_value)

    val torrentsSortMode: MutableProperty<TorrentsListFragmentViewModel.SortMode> =
        mutableProperty<Int>(
            R.string.torrents_sort_mode_key,
            R.integer.torrents_sort_mode_default_value
        ).map(
            transformGetter = { enumFromInt(it, TorrentsListFragmentViewModel.SortMode.DEFAULT) },
            transformSetter = { it.ordinal }
        )

    val torrentsSortOrder: MutableProperty<TorrentsListFragmentViewModel.SortOrder> =
        mutableProperty<Int>(
            R.string.torrents_sort_order_key,
            R.integer.torrents_sort_order_default_value
        ).map(
            transformGetter = { enumFromInt(it, TorrentsListFragmentViewModel.SortOrder.DEFAULT) },
            transformSetter = { it.ordinal }
        )

    val torrentsStatusFilter: MutableProperty<TorrentsListFragmentViewModel.StatusFilterMode> =
        mutableProperty<Int>(
            R.string.torrents_status_filter_key,
            R.integer.torrents_status_filter_default_value
        ).map(
            transformGetter = {
                enumFromInt(
                    it,
                    TorrentsListFragmentViewModel.StatusFilterMode.DEFAULT
                )
            },
            transformSetter = { it.ordinal }
        )

    val torrentsTrackerFilter: MutableProperty<String> = mutableProperty(
        R.string.torrents_tracker_filter_key,
        R.string.torrents_tracker_filter_default_value
    )

    val torrentsDirectoryFilter: MutableProperty<String> = mutableProperty(
        R.string.torrents_directory_filter_key,
        R.string.torrents_directory_filter_default_value
    )

    private fun <T : Any> property(
        kClass: KClass<T>,
        @StringRes keyResId: Int,
        @AnyRes defaultValueResId: Int,
        key: String = context.getString(keyResId)
    ): Property<T> {
        @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
        val defaultValue = when (kClass) {
            Boolean::class -> context.resources.getBoolean(defaultValueResId)
            Float::class -> context.resources.getDimension(defaultValueResId)
            Int::class -> context.resources.getInteger(defaultValueResId)
            Long::class -> context.resources.getInteger(defaultValueResId).toLong()
            String::class -> context.getString(defaultValueResId)
            Set::class -> context.resources.getStringArray(defaultValueResId).toSet()
            else -> throw IllegalArgumentException("Unsupported property type $kClass")
        } as T

        val getter = getSharedPreferencesGetter(kClass)

        return object : Property<T> {
            override val key = key

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
        @AnyRes defaultValueResId: Int
    ): Property<T> = property(T::class, keyResId, defaultValueResId)

    private fun <T : Any> mutableProperty(
        kClass: KClass<T>,
        @StringRes keyResId: Int,
        @AnyRes defaultValueResId: Int
    ): MutableProperty<T> {
        val key = context.getString(keyResId)
        val property = property(kClass, keyResId, defaultValueResId, key = key)
        val setter = getSharedPreferencesSetter(kClass)
        return object : MutableProperty<T>, Property<T> by property {
            override suspend fun set(value: T) = withContext(Dispatchers.IO) {
                migrate()
                preferences.setter(key, value)
            }
        }
    }

    private inline fun <reified T : Any> mutableProperty(
        @StringRes keyResId: Int,
        @AnyRes defaultValueResId: Int
    ): MutableProperty<T> = mutableProperty(T::class, keyResId, defaultValueResId)

    interface Property<T : Any> {
        val key: String
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
        override val key = this@map.key
        override suspend fun get() = transform(this@map.get())
        override fun flow() = this@map.flow().map(transform)
    }

private fun <T : Any, R : Any> Settings.MutableProperty<T>.map(
    transformGetter: suspend (T) -> R,
    transformSetter: suspend (R) -> T
) =
    object : Settings.MutableProperty<R> {
        override val key = this@map.key
        override suspend fun get() = transformGetter(this@map.get())
        override fun flow(): Flow<R> = this@map.flow().map(transformGetter)
        override suspend fun set(value: R) = this@map.set(transformSetter(value))
    }
