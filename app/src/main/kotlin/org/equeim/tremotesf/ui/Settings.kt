// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
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
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


@SuppressLint("StaticFieldLeak")
object Settings {
    private val context: Context = TremotesfApplication.instance
    private val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    @Volatile
    private var migrated = false
    private val migrationMutex = Mutex()

    private const val DEPRECATED_DARK_THEME_KEY = "darkTheme"
    private const val DEPRECATED_OLD_COLORS_KEY = "oldColors"
    private const val DEPRECATED_THEME_KEY = "theme"
    private const val DEPRECATED_THEME_VALUE_AUTO = "auto"
    private const val DEPRECATED_THEME_VALUE_DARK = "dark"
    private const val DEPRECATED_THEME_VALUE_LIGHT = "light"
    private const val DEPRECATED_REMEMBER_DOWNLOAD_DIRECTORY_KEY = "rememberDownloadDirectory"

    private suspend fun migrate() {
        if (migrated) return
        migrationMutex.withLock {
            if (migrated) return
            withContext(Dispatchers.IO) {
                if (preferences.contains(DEPRECATED_DARK_THEME_KEY)) {
                    preferences.edit {
                        putString(
                            darkThemeMode.key,
                            if (preferences.getBoolean(DEPRECATED_DARK_THEME_KEY, false)) {
                                DarkThemeMode.On.prefsValue
                            } else {
                                DarkThemeMode.Off.prefsValue
                            }
                        )
                        remove(DEPRECATED_DARK_THEME_KEY)
                    }
                }
                if (preferences.contains(DEPRECATED_OLD_COLORS_KEY)) {
                    preferences.edit {
                        putString(
                            colorTheme.key,
                            if (preferences.getBoolean(DEPRECATED_OLD_COLORS_KEY, false)) {
                                ColorTheme.Teal.prefsValue
                            } else {
                                ColorTheme.Red.prefsValue
                            }
                        )
                        remove(DEPRECATED_OLD_COLORS_KEY)
                    }
                }
                if (preferences.contains(DEPRECATED_THEME_KEY)) {
                    preferences.edit {
                        val darkThemeModeValue =
                            when (preferences.getString(DEPRECATED_THEME_KEY, null)) {
                                DEPRECATED_THEME_VALUE_AUTO -> DarkThemeMode.Auto
                                DEPRECATED_THEME_VALUE_DARK -> DarkThemeMode.On
                                DEPRECATED_THEME_VALUE_LIGHT -> DarkThemeMode.Off
                                else -> null
                            }
                        darkThemeModeValue?.let { putString(darkThemeMode.key, it.prefsValue) }
                        remove(DEPRECATED_THEME_KEY)
                    }
                }
                if (
                    (preferences.getString(colorTheme.key, null) == ColorTheme.System.prefsValue)
                    && !DynamicColors.isDynamicColorAvailable()
                ) {
                    val newValue = COLOR_THEME_DEFAULT_VALUE.prefsValue
                    Timber.e("Dynamic colors are not supported, setting ${colorTheme.key} value to $newValue")
                    preferences.edit {
                        putString(colorTheme.key, newValue)
                    }
                }
                if (preferences.contains(DEPRECATED_REMEMBER_DOWNLOAD_DIRECTORY_KEY)) {
                    preferences.edit {
                        putBoolean(
                            rememberAddTorrentParameters.key,
                            preferences.getBoolean(DEPRECATED_REMEMBER_DOWNLOAD_DIRECTORY_KEY, false)
                        )
                        remove(DEPRECATED_REMEMBER_DOWNLOAD_DIRECTORY_KEY)
                    }
                }
                migrated = true
            }
        }
    }

    enum class ColorTheme(
        override val prefsValue: String,
        @StyleRes val activityThemeResId: Int = 0,
    ) : MappedPrefsEnum {
        System("system"),
        Red("red", R.style.AppTheme),
        Teal("teal", R.style.AppTheme_Teal);
    }

    private val COLOR_THEME_DEFAULT_VALUE = ColorTheme.Red

    private val colorThemeMapper =
        EnumPrefsMapper<ColorTheme>(COLOR_THEME_DEFAULT_VALUE)
    val colorTheme: Property<ColorTheme> = PrefsProperty<String>(
        key = "colorTheme",
        defaultValue = COLOR_THEME_DEFAULT_VALUE.prefsValue
    ).map(
        prefsToMapped = colorThemeMapper::prefsValueToEnum,
        mappedToPrefs = ColorTheme::prefsValue
    )

    enum class DarkThemeMode(override val prefsValue: String, val nightMode: Int) :
        MappedPrefsEnum {
        Auto(
            "auto",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else {
                AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            }
        ),
        On("on", AppCompatDelegate.MODE_NIGHT_YES),
        Off("off", AppCompatDelegate.MODE_NIGHT_NO);
    }

    private val DARK_THEME_MODE_DEFAULT_VALUE = DarkThemeMode.Auto
    private val darkThemeModeMapper = EnumPrefsMapper<DarkThemeMode>(DARK_THEME_MODE_DEFAULT_VALUE)

    val darkThemeMode: Property<DarkThemeMode> =
        PrefsProperty<String>(
            key = "darkThemeMode",
            defaultValue = DARK_THEME_MODE_DEFAULT_VALUE.prefsValue
        ).map(prefsToMapped = darkThemeModeMapper::prefsValueToEnum, mappedToPrefs = DarkThemeMode::prefsValue)

    val torrentCompactView: Property<Boolean> = PrefsProperty(key = "torrentCompactView", defaultValue = false)

    val torrentNameMultiline: Property<Boolean> = PrefsProperty(key = "torrentNameMultiline", defaultValue = false)

    val quickReturn: Property<Boolean> = PrefsProperty(key = "quickReturn", defaultValue = false)

    val showPersistentNotification: Property<Boolean> =
        PrefsProperty(key = "persistentNotification", defaultValue = false)

    val notifyOnFinished: Property<Boolean> = PrefsProperty(key = "notifyOnFinished", defaultValue = true)

    val notifyOnAdded: Property<Boolean> = PrefsProperty(key = "notifyOnAdded", defaultValue = false)

    enum class BackgroundUpdateInterval(override val prefsValue: String, val duration: Duration) : MappedPrefsEnum {
        Disabled("0", Duration.ZERO),
        FifteenMinutes("15", 15.minutes),
        ThirtyMinutes("30", 30.minutes),
        Hour("60", 1.hours),
        ThreeHours("180", 3.hours),
        SixHours("360", 6.hours),
        TwelveHours("720", 12.hours),
        Day("1440", 1.days)
    }

    private val BACKGROUND_UPDATE_INTERVAL_DEFAULT_VALUE = BackgroundUpdateInterval.Disabled
    private val backgroundUpdateIntervalMapper = EnumPrefsMapper(BACKGROUND_UPDATE_INTERVAL_DEFAULT_VALUE)

    val backgroundUpdateInterval: Property<BackgroundUpdateInterval> = PrefsProperty<String>(
        key = "backgroundUpdateInterval",
        defaultValue = BACKGROUND_UPDATE_INTERVAL_DEFAULT_VALUE.prefsValue
    ).map(
        prefsToMapped = backgroundUpdateIntervalMapper::prefsValueToEnum,
        mappedToPrefs = BackgroundUpdateInterval::prefsValue
    )

    val notifyOnFinishedSinceLastConnection: Property<Boolean> =
        PrefsProperty(key = "notifyOnFinishedSinceLast", defaultValue = false)

    val notifyOnAddedSinceLastConnection: Property<Boolean> =
        PrefsProperty(key = "notifyOnAddedSinceLast", defaultValue = false)

    val userDismissedNotificationPermissionRequest: Property<Boolean> =
        PrefsProperty(key = "userDismissedNotificationPermissionRequest", defaultValue = false)

    val deleteFiles: Property<Boolean> = PrefsProperty(key = "deleteFiles", defaultValue = false)

    val fillTorrentLinkFromClipboard: Property<Boolean> =
        PrefsProperty(key = "fillTorrentLinkFromClipboard", defaultValue = false)

    val rememberAddTorrentParameters: Property<Boolean> =
        PrefsProperty(key = "rememberAddTorrentParameters", defaultValue = true)

    enum class StartTorrentAfterAdding(override val prefsValue: String) : MappedPrefsEnum {
        Start("start"),
        DontStart("dont_start"),
        Unknown("unknown")
    }

    private val START_TORRENT_AFTER_ADDING_DEFAULT_VALUE = StartTorrentAfterAdding.Unknown
    private val startTorrentAfterAddingMapper =
        EnumPrefsMapper<StartTorrentAfterAdding>(START_TORRENT_AFTER_ADDING_DEFAULT_VALUE)
    val lastAddTorrentStartAfterAdding: Property<StartTorrentAfterAdding> = PrefsProperty<String>(
        key = "lastAddTorrentStartAfterAdding",
        defaultValue = START_TORRENT_AFTER_ADDING_DEFAULT_VALUE.prefsValue
    ).map(
        prefsToMapped = startTorrentAfterAddingMapper::prefsValueToEnum,
        mappedToPrefs = startTorrentAfterAddingMapper.enumToPrefsValue
    )

    private val BANDWIDTH_PRIORITY_DEFAULT_VALUE = TorrentLimits.BandwidthPriority.Normal
    private val bandwidthPriorityMapper =
        EnumPrefsMapper<TorrentLimits.BandwidthPriority>(BANDWIDTH_PRIORITY_DEFAULT_VALUE) {
            when (it) {
                TorrentLimits.BandwidthPriority.Low -> "low"
                TorrentLimits.BandwidthPriority.Normal -> "normal"
                TorrentLimits.BandwidthPriority.High -> "high"
            }
        }
    val lastAddTorrentPriority: Property<TorrentLimits.BandwidthPriority> = PrefsProperty<String>(
        key = "lastAddTorrentPriority",
        defaultValue = bandwidthPriorityMapper.enumToPrefsValue(BANDWIDTH_PRIORITY_DEFAULT_VALUE)
    ).map(
        prefsToMapped = bandwidthPriorityMapper::prefsValueToEnum,
        mappedToPrefs = bandwidthPriorityMapper.enumToPrefsValue
    )

    val lastAddTorrentLabels: Property<Set<String>> = PrefsProperty(
        key = "lastAddTorrentLabels",
        defaultValue = emptySet()
    )

    val mergeTrackersWhenAddingExistingTorrent: Property<Boolean> =
        PrefsProperty(key = "mergeTrackersWhenAddingExistingTorrent", defaultValue = true)

    val askForMergingTrackersWhenAddingExistingTorrent: Property<Boolean> =
        PrefsProperty(key = "askForMergingTrackersWhenAddingExistingTorrent", defaultValue = true)

    val torrentsSortMode: Property<TorrentsListFragmentViewModel.SortMode> =
        PrefsProperty<Int>(key = "torrentsSortMode", defaultValue = -1).map(
            prefsToMapped = {
                TorrentsListFragmentViewModel.SortMode.entries.getOrElse(it) {
                    TorrentsListFragmentViewModel.SortMode.DEFAULT
                }
            },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsSortOrder: Property<TorrentsListFragmentViewModel.SortOrder> =
        PrefsProperty<Int>(key = "torrentsSortOrder", defaultValue = -1).map(
            prefsToMapped = {
                TorrentsListFragmentViewModel.SortOrder.entries.getOrElse(it) {
                    TorrentsListFragmentViewModel.SortOrder.DEFAULT
                }
            },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsStatusFilter: Property<TorrentsListFragmentViewModel.StatusFilterMode> =
        PrefsProperty<Int>(key = "torrentsStatusFilter", defaultValue = -1).map(
            prefsToMapped = {
                TorrentsListFragmentViewModel.StatusFilterMode.entries.getOrElse(it) {
                    TorrentsListFragmentViewModel.StatusFilterMode.DEFAULT
                }
            },
            mappedToPrefs = { it.ordinal }
        )

    val torrentsLabelFilter: Property<String> = PrefsProperty(key = "torrentsLabelFilter", defaultValue = "")

    val torrentsTrackerFilter: Property<String> = PrefsProperty(key = "torrentsTrackerFilter", defaultValue = "")

    val torrentsDirectoryFilter: Property<String> = PrefsProperty(key = "torrentsFolderFilter", defaultValue = "")

    interface Property<T : Any> {
        val key: String
        suspend fun get(): T
        fun flow(): Flow<T>
        suspend fun set(value: T)
    }

    private inline fun <reified T : Any> PrefsProperty(
        key: String,
        defaultValue: T,
    ) = PrefsProperty(T::class, key, defaultValue)

    private class PrefsProperty<T : Any>(
        kClass: KClass<T>,
        override val key: String,
        private val defaultValue: T,
    ) : Property<T> {
        private val getter = getSharedPreferencesGetter(kClass)
        private val setter = getSharedPreferencesSetter(kClass)

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
        private val defaultValue: T,
        val enumToPrefsValue: (T) -> String,
    ) {
        private val enumValues = requireNotNull(enumClass.enumConstants)

        fun prefsValueToEnum(prefsValue: String): T {
            enumValues.find { enumToPrefsValue(it) == prefsValue }?.let { return it }
            Timber.e("Unknown prefs value $prefsValue for enum $enumClass")
            return defaultValue
        }
    }

    private inline fun <reified T : Enum<T>> EnumPrefsMapper(
        defaultValue: T,
        noinline enumToPrefsValue: (T) -> String,
    ): EnumPrefsMapper<T> =
        EnumPrefsMapper(T::class.java, defaultValue, enumToPrefsValue)

    private interface MappedPrefsEnum {
        val prefsValue: String
    }

    private inline fun <reified T> EnumPrefsMapper(
        defaultValue: T
    ): EnumPrefsMapper<T> where T : MappedPrefsEnum, T : Enum<T> =
        EnumPrefsMapper(T::class.java, defaultValue, MappedPrefsEnum::prefsValue)
}
