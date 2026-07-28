// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.torrentslist.TorrentsListDestination
import timber.log.Timber


class NavigationActivity : ComponentActivity() {
    companion object {
        private val createdActivities = mutableListOf<NavigationActivity>()

        fun finishAllActivities() = createdActivities.apply {
            forEach(Activity::finishAndRemoveTask)
            clear()
        }

        private fun Configuration.nightModeString(): String? = when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "YES"
            Configuration.UI_MODE_NIGHT_NO -> "NO"
            Configuration.UI_MODE_NIGHT_UNDEFINED -> "UNDEFINED"
            else -> null
        }
    }

    private val model by viewModels<NavigationActivityViewModel>()

    private val deepLinkDestinations = Channel<Destination>(Channel.CONFLATED)

    private lateinit var initialDarkThemeMode: Settings.DarkThemeMode

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            initialDarkThemeMode = ActivityThemeProvider.darkThemeMode.value
            when (initialDarkThemeMode) {
                Settings.DarkThemeMode.On, Settings.DarkThemeMode.Off -> {
                    Timber.d("Overriding night mode for dark theme mode $initialDarkThemeMode")
                    val config = Configuration()
                    config.uiMode = if (initialDarkThemeMode == Settings.DarkThemeMode.On) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    }
                    applyOverrideConfiguration(config)
                }

                Settings.DarkThemeMode.Auto ->
                    Timber.d("Not overriding night mode for dark theme mode $initialDarkThemeMode")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate() called with: savedInstanceState = $savedInstanceState")
        Timber.i("onCreate: intent = $intent")

        super.onCreate(savedInstanceState)
        createdActivities.add(this)
        AppForegroundTracker.registerActivity(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        Timber.d("Night mode is ${resources.configuration.nightModeString()}")

        val initialDestinations = model.getInitialDestinations(intent, isTaskRoot)
        Timber.d("Initial destinations = $initialDestinations")
        setContent {
            ApplicationTheme {
                val viewModelStoreDecorator =
                    rememberExtendedViewModelStoreNavEntryDecorator<NavController.BackStackEntry>()
                val navController = rememberNavController(
                    initialDestinations = initialDestinations,
                    viewModelStoreDecorator = viewModelStoreDecorator
                )

                LaunchedEffect(Unit) {
                    for (destination in deepLinkDestinations) {
                        navController.apply {
                            if (isTaskRoot) {
                                popUpTo<TorrentsListDestination>()
                                navigateTo(destination)
                            } else {
                                resetFirstDestination(destination)
                            }
                        }
                    }
                }

                NavDisplay(
                    backStack = navController.backStack,
                    onBack = navController::popBackStack,
                    sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
                    entryProvider = { key ->
                        NavEntry(
                            key = key,
                            contentKey = key.contentKey,
                            metadata = key.destination.metadata
                        ) { key.destination.Content(navController) }
                    },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        viewModelStoreDecorator
                    ),
                    // Use the same animation as when navigation back though button on the toolbar to get rid of ugly scaling animation
                    // We need to do it like that because predictivePopTransitionSpec takes a parameter which we want to ignore
                    predictivePopTransitionSpec = defaultPopTransitionSpec<NavController.BackStackEntry>()
                        .let { popSpec -> { popSpec() } },
                    modifier = Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = model::shouldStartDragAndDrop,
                        target = object : DragAndDropTarget {
                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                Timber.i("Received onDrop event")
                                val permissions = requestDragAndDropPermissions(event.toAndroidDragEvent())
                                val destination = model.getAddTorrentDestination(event)
                                return if (destination != null) {
                                    deepLinkDestinations.trySend(destination)
                                    Timber.i("Accepting onDrop event")
                                    true
                                } else {
                                    Timber.i("Rejecting onDrop event")
                                    permissions?.release()
                                    false
                                }
                            }
                        }
                    )
                )
            }
        }

        ForegroundService.startStopAutomatically()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                val newMode = ActivityThemeProvider.darkThemeMode.first { it != initialDarkThemeMode }
                Timber.d("Dark theme mode changed to $newMode, recreating activity")
                ActivityCompat.recreate(this@NavigationActivity)
            }
        }

        Timber.i("onCreate: return")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Timber.d("onConfigurationChanged() called with: newConfig = $newConfig")
        super.onConfigurationChanged(newConfig)
        Timber.d("onConfigurationChanged: night mode is ${newConfig.nightModeString()}")
        // These properties are set by Activity once on creation, so we need to update them ourselves on configuration change
        WindowInsetsControllerCompat(window, findViewById(android.R.id.content)).apply {
            val isLight = resources.getBoolean(R.bool.is_light_theme)
            isAppearanceLightStatusBars = isLight
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = getColor(R.color.navigation_bar_color)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                isAppearanceLightNavigationBars = isLight
            }
            window.setBackgroundDrawableResource(R.color.window_background)
        }
    }

    override fun onStart() {
        Timber.i("onStart() called")
        super.onStart()
    }

    override fun onStop() {
        Timber.i("onStop() called")
        super.onStop()
    }

    override fun onDestroy() {
        Timber.i("onDestroy() called")
        createdActivities.remove(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        Timber.i("onNewIntent() called with: intent = $intent")
        super.onNewIntent(intent)
        model.getDeepLinkDestination(intent)?.let(deepLinkDestinations::trySend)
    }
}
