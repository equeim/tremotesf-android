// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.TorrentLimits
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
                context.getString(R.string.deprecated_prefs_remember_download_directory_key)
                    .let { deprecatedRememberDownloadDirectoryKey ->
                        if (preferences.contains(deprecatedRememberDownloadDirectoryKey)) {
                            preferences.edit {
                                putBoolean(
                                    rememberAddTorrentParameters.key,
                                    preferences.getBoolean(deprecatedRememberDownloadDirectoryKey, false)
                                )
                                remove(deprecatedRememberDownloadDirectoryKey)
                            }
                        }
                    }
                migrated = true
            }
        }
    }

    enum class ColorTheme(
        @StringRes prefsValueResId: Int,
        @StyleRes val activityThemeResId: Int = 0,
    ) : MappedPrefsEnum {
        System(R.string.prefs_color_theme_value_system),
        Red(R.string.prefs_color_theme_value_red, R.style.AppTheme),
        Teal(R.string.prefs_color_theme_value_teal, R.style.AppTheme_Teal);

        override val prefsValue = context.getString(prefsValueResId)
    }

    private val colorThemeMapper =
        EnumPrefsMapper<ColorTheme>(R.string.prefs_color_theme_key, R.string.prefs_color_theme_default_value)
    val colorTheme: Property<ColorTheme> = PrefsProperty<String>(
        R.string.prefs_color_theme_key,
        R.string.prefs_color_theme_default_value
    ).map(
        prefsToMapped = colorThemeMapper::prefsValueToEnum,
        mappedToPrefs = ColorTheme::prefsValue
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
    }

    private val darkThemeModeMapper = EnumPrefsMapper<DarkThemeMode>(
        R.string.prefs_dark_theme_mode_key,
        R.string.prefs_dark_theme_mode_default_value
    )
    val darkThemeMode: Property<DarkThemeMode> =
        PrefsProperty<String>(
            R.string.prefs_dark_theme_mode_key,
            R.string.prefs_dark_theme_mode_default_value
        ).map(prefsToMapped = darkThemeModeMapper::prefsValueToEnum, mappedToPrefs = DarkThemeMode::prefsValue)

    val torrentCompactView: Property<Boolean> = PrefsProperty(
        R.string.prefs_torrent_compact_view_key,
        R.bool.prefs_torrent_compact_view_default_value
    )

    val torrentNameMultiline: Property<Boolean> = PrefsProperty(
        R.string.prefs_torrent_name_multiline_key,
        R.bool.prefs_torrent_name_multiline_default_value
    )

    val quickReturn: Property<Boolean> =
        PrefsProperty(R.string.prefs_quick_return, R.bool.prefs_quick_return_default_value)

    val showPersistentNotification: Property<Boolean> = PrefsProperty(
        R.string.prefs_persistent_notification_key,
        R.bool.prefs_persistent_notification_default_value
    )

    val notifyOnFinished: Property<Boolean> = PrefsProperty(
        R.string.prefs_notify_on_finished_key,
        R.bool.prefs_notify_on_finished_default_value
    )

    val notifyOnAdded: Property<Boolean> =
        PrefsProperty(R.string.prefs_notify_on_added_key, R.bool.prefs_notify_on_added_default_value)

    val backgroundUpdateInterval: Property<Long> = PrefsProperty<String>(
        R.string.prefs_background_update_interval_key,
        R.string.prefs_background_update_interval_default_value
    ).map(
        prefsToMapped = {
            try {
                it.toLong()
            } catch (ignore: NumberFormatException) {
                0
            }
        },
        mappedToPrefs = Long::toString
    )

    val notifyOnFinishedSinceLastConnection: Property<Boolean> =
        PrefsProperty(
            R.string.prefs_notify_on_finished_since_last_key,
            R.bool.prefs_notify_on_finished_since_last_default_value
        )

    val notifyOnAddedSinceLastConnection: Property<Boolean> =
        PrefsProperty(
            R.string.prefs_notify_on_added_since_last_key,
            R.bool.prefs_notify_on_added_since_last_default_value
        )

    val userDismissedNotificationPermissionRequest: Property<Boolean> =
        PrefsProperty(
            R.string.prefs_user_dismissed_notification_permission_request_key,
            R.bool.prefs_user_dismissed_notification_permission_request_default_value
        )

    val deleteFiles: Property<Boolean> =
        PrefsProperty(R.string.prefs_delete_files_key, R.bool.prefs_delete_files_default_value)

    val fillTorrentLinkFromKeyboard: Property<Boolean> =
        PrefsProperty(R.string.prefs_link_from_clipboard_key, R.bool.prefs_link_from_clipboard_default_value)

    val rememberAddTorrentParameters: Property<Boolean> =
        PrefsProperty(
            R.string.prefs_remember_add_torrent_parameters_key,
            R.bool.prefs_remember_add_torrent_parameters_default_value
        )

    enum class StartTorrentAfterAdding(override val prefsValue: String) : MappedPrefsEnum {
        Start("start"),
        DontStart("dont_start"),
        Unknown("unknown")
    }

    private val startTorrentAfterAddingMapper = EnumPrefsMapper<StartTorrentAfterAdding>(
        R.string.prefs_last_add_torrent_start_after_adding_key,
        R.string.prefs_last_add_torrent_start_after_adding_default_value
    )
    val lastAddTorrentStartAfterAdding: Property<StartTorrentAfterAdding> = PrefsProperty<String>(
        R.string.prefs_last_add_torrent_start_after_adding_key,
        R.string.prefs_last_add_torrent_start_after_adding_default_value
    ).map(
        prefsToMapped = startTorrentAfterAddingMapper::prefsValueToEnum,
        mappedToPrefs = startTorrentAfterAddingMapper.enumToPrefsValue
    )

    private val bandwidthPriorityMapper = EnumPrefsMapper<TorrentLimits.BandwidthPriority>(
        R.string.prefs_last_add_torrent_priority_key,
        R.string.prefs_last_add_torrent_priority_default_value
    ) {
        when (it) {
            TorrentLimits.BandwidthPriority.Low -> "low"
            TorrentLimits.BandwidthPriority.Normal -> "normal"
            TorrentLimits.BandwidthPriority.High -> "high"
        }
    }
    val lastAddTorrentPriority: Property<TorrentLimits.BandwidthPriority> = PrefsProperty<String>(
        R.string.prefs_last_add_torrent_priority_key,
        R.string.prefs_last_add_torrent_priority_default_value
    ).map(
        prefsToMapped = bandwidthPriorityMapper::prefsValueToEnum,
        mappedToPrefs = bandwidthPriorityMapper.enumToPrefsValue
    )

    val torrentsSortMode: Property<TorrentsListFragmentViewModel.SortMode> =
        PrefsProperty<Int>(
            R.string.torrents_sort_mode_key,
            R.integer.torrents_sort_mode_default_value
        ).map(
            prefsToMapped = { TorrentsListFragmentViewModel.SortMode.entries.getOrElse(it) { TorrentsListFragmentViewModel.SortMode.DEFAULT } },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsSortOrder: Property<TorrentsListFragmentViewModel.SortOrder> =
        PrefsProperty<Int>(
            R.string.torrents_sort_order_key,
            R.integer.torrents_sort_order_default_value
        ).map(
            prefsToMapped = { TorrentsListFragmentViewModel.SortOrder.entries.getOrElse(it) { TorrentsListFragmentViewModel.SortOrder.DEFAULT } },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsStatusFilter: Property<TorrentsListFragmentViewModel.StatusFilterMode> =
        PrefsProperty<Int>(
            R.string.torrents_status_filter_key,
            R.integer.torrents_status_filter_default_value
        ).map(
            prefsToMapped = { TorrentsListFragmentViewModel.StatusFilterMode.entries.getOrElse(it) { TorrentsListFragmentViewModel.StatusFilterMode.DEFAULT } },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsTrackerFilter: Property<String> = PrefsProperty(
        R.string.torrents_tracker_filter_key,
        R.string.torrents_tracker_filter_default_value
    )

    val torrentsDirectoryFilter: Property<String> = PrefsProperty(
        R.string.torrents_directory_filter_key,
        R.string.torrents_directory_filter_default_value
    )

    interface Property<T : Any> {
        val key: String
        suspend fun get(): T
        fun flow(): Flow<T>
        suspend fun set(value: T)
    }

    private inline fun <reified T : Any> PrefsProperty(
        @StringRes keyResId: Int,
        @AnyRes defaultValueResId: Int,
    ) = PrefsProperty(T::class, keyResId, defaultValueResId)

    private class PrefsProperty<T : Any>(
        kClass: KClass<T>,
        @StringRes keyResId: Int,
        @AnyRes defaultValueResId: Int,
    ) : Property<T> {
        private val defaultValue = getDefaultValue(kClass, defaultValueResId)
        private val getter = getSharedPreferencesGetter(kClass)
        private val setter = getSharedPreferencesSetter(kClass)

        override val key: String = context.getString(keyResId)

        override suspend fun get(): T = withContext(Dispatchers.IO) {
            migrate()
            preferences.getter(key, defaultValue)
        }

        override fun flow(): Flow<T> = callbackFlow {
            send(get())
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
                if (changedKey == key) {
                    launch { send(get()) }
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.flowOn(Dispatchers.IO)

        override suspend fun set(value: T) = withContext(Dispatchers.IO) {
            migrate()
            preferences.edit { setter(key, value) }
        }

        private companion object {
            private fun <T : Any> getDefaultValue(kClass: KClass<T>, @AnyRes defaultValueResId: Int): T {
                @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
                return when (kClass) {
                    Boolean::class -> context.resources.getBoolean(defaultValueResId)
                    Float::class -> context.resources.getDimension(defaultValueResId)
                    Int::class -> context.resources.getInteger(defaultValueResId)
                    Long::class -> context.resources.getInteger(defaultValueResId).toLong()
                    String::class -> context.getString(defaultValueResId)
                    Set::class -> context.resources.getStringArray(defaultValueResId).toSet()
                    else -> throw IllegalArgumentException("Unsupported property type $kClass")
                } as T
            }

            private fun <T : Any> getSharedPreferencesGetter(kClass: KClass<T>): SharedPreferences.(String, T) -> T {
                @Suppress("UNCHECKED_CAST")
                return when (kClass) {
                    Boolean::class -> SharedPreferences::getBoolean
                    Float::class -> SharedPreferences::getFloat
                    Int::class -> SharedPreferences::getInt
                    Long::class -> SharedPreferences::getLong
                    String::class -> SharedPreferences::getString
                    Set::class -> SharedPreferences::getStringSet
                    else -> throw IllegalArgumentException("Unsupported property type $kClass")
                } as SharedPreferences.(String, T) -> T
            }

            private fun <T : Any> getSharedPreferencesSetter(kClass: KClass<T>): SharedPreferences.Editor.(String, T) -> Unit {
                @Suppress("UNCHECKED_CAST")
                return when (kClass) {
                    Boolean::class -> SharedPreferences.Editor::putBoolean
                    Float::class -> SharedPreferences.Editor::putFloat
                    Int::class -> SharedPreferences.Editor::putInt
                    Long::class -> SharedPreferences.Editor::putLong
                    String::class -> SharedPreferences.Editor::putString
                    Set::class -> SharedPreferences.Editor::putStringSet
                    else -> throw IllegalArgumentException("Unsupported property type $kClass")
                } as SharedPreferences.Editor.(String, T) -> Unit
            }
        }
    }

    private fun <T : Any, R : Any> Property<T>.map(prefsToMapped: (T) -> R, mappedToPrefs: (R) -> T) =
        MappedProperty(this, prefsToMapped, mappedToPrefs)

    private class MappedProperty<T : Any, R : Any>(
        private val prefsProperty: Property<T>,
        private val prefsToMapped: (T) -> R,
        private val mappedToPrefs: (R) -> T,
    ) : Property<R> {
        override val key: String get() = prefsProperty.key
        override suspend fun get(): R = prefsToMapped(prefsProperty.get())
        override fun flow(): Flow<R> = prefsProperty.flow().map(prefsToMapped)
        override suspend fun set(value: R) = prefsProperty.set(mappedToPrefs(value))
    }

    private class EnumPrefsMapper<T : Enum<T>>(
        private val enumClass: Class<T>,
        @StringRes private val keyResId: Int,
        @StringRes private val defaultValueResId: Int,
        val enumToPrefsValue: (T) -> String,
    ) {
        private val enumValues = requireNotNull(enumClass.enumConstants)

        fun prefsValueToEnum(prefsValue: String): T {
            enumValues.find { enumToPrefsValue(it) == prefsValue }?.let { return it }
            val key = context.getString(keyResId)
            Timber.e("Unknown prefs value $prefsValue for key $key and enum $enumClass")
            val defaultPrefsValue = context.getString(defaultValueResId)
            return enumValues.find { enumToPrefsValue(it) == defaultPrefsValue }
                ?: throw IllegalStateException("Did not find value of enum $enumClass for default prefs value $defaultPrefsValue and key $key")
        }
    }

    private inline fun <reified T : Enum<T>> EnumPrefsMapper(
        @StringRes keyResId: Int,
        @StringRes defaultValueResId: Int,
        noinline enumToPrefsValue: (T) -> String,
    ): EnumPrefsMapper<T> =
        EnumPrefsMapper(T::class.java, keyResId, defaultValueResId, enumToPrefsValue)

    private interface MappedPrefsEnum {
        val prefsValue: String
    }

    private inline fun <reified T> EnumPrefsMapper(
        @StringRes keyResId: Int,
        @StringRes defaultValueResId: Int,
    ): EnumPrefsMapper<T> where T : MappedPrefsEnum, T : Enum<T> =
        EnumPrefsMapper(T::class.java, keyResId, defaultValueResId, MappedPrefsEnum::prefsValue)
}
