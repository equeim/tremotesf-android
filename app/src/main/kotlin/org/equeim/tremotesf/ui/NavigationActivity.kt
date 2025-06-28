// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.annotation.AnimatorRes
import androidx.annotation.IdRes
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.NavigationActivityBinding
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.utils.hideKeyboard
import timber.log.Timber


class NavigationActivity : FragmentActivity() {
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

    private lateinit var binding: NavigationActivityBinding

    private lateinit var navController: NavController

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

        // https://issuetracker.google.com/issues/342919181
        @OptIn(PredictiveBackControl::class)
        FragmentManager.enablePredictiveBack(false)

        super.onCreate(savedInstanceState)
        createdActivities.add(this)
        AppForegroundTracker.registerActivity(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        Timber.d("Night mode is ${resources.configuration.nightModeString()}")

        overrideIntentWithDeepLink()

        binding = NavigationActivityBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        navController.addOnDestinationChangedListener { _, _, _ ->
            hideKeyboard()
        }
        handleDropEvents()

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

    private fun overrideIntentWithDeepLink() {
        if (model.navigatedInitially) return
        model.navigatedInitially = true

        val intent = model.getInitialDeepLinkIntent(intent) ?: return
        Timber.i("overrideIntentWithDeepLink: intent = $intent")
        this.intent = intent
    }

    private fun handleDropEvents() {
        binding.root.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    Timber.d("Handling drag start event")
                    model.acceptDragStartEvent(event.clipDescription)
                }

                DragEvent.ACTION_DROP -> {
                    Timber.d("Handling drop event")
                    val directions = model.getAddTorrentDirections(event.clipData)
                    if (directions != null) {
                        requestDragAndDropPermissions(event)
                        navController.navigate(
                            directions.destinationId,
                            directions.arguments,
                            NavOptions.Builder()
                                .setPopUpTo(navController.graph.startDestinationId, false)
                                .build()
                        )
                    }
                    directions != null
                }
                /**
                 * Don't enter [also] branch to avoid log spam
                 */
                else -> return@setOnDragListener false
            }.also {
                if (it) {
                    Timber.d("Accepting event")
                } else {
                    Timber.d("Rejecting event")
                }
            }
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
        model.getAddTorrentDirections(intent)?.let { (destinationId, arguments) ->
            navController.navigate(
                destinationId,
                arguments,
                NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, false)
                    .build()
            )
        }
    }
}

class NavHostFragment : NavHostFragment() {
    override fun onCreateNavHostController(navHostController: NavHostController) {
        super.onCreateNavHostController(navHostController)
        navHostController.addOnDestinationChangedListener { _, destination, _ ->
            Timber.i("Destination changed: destination = $destination")
        }
    }

    @Suppress("OverridingDeprecatedMember", "OVERRIDE_DEPRECATION")
    override fun createFragmentNavigator(): Navigator<out androidx.navigation.fragment.FragmentNavigator.Destination> {
        return FragmentNavigator(requireContext(), childFragmentManager, id)
    }

    // NavController doesn't set any pop animations when handling deep links
    // Use this workaround to always set pop animations
    @Navigator.Name("fragment")
    class FragmentNavigator(
        context: Context,
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
    ) : androidx.navigation.fragment.FragmentNavigator(context, fragmentManager, containerId) {
        override fun navigate(
            entries: List<NavBackStackEntry>,
            navOptions: NavOptions?,
            navigatorExtras: Navigator.Extras?,
        ) = super.navigate(entries, navOptions?.overridePopAnimations(), navigatorExtras)

        override fun navigate(
            destination: Destination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Navigator.Extras?,
        ) = super.navigate(destination, args, navOptions?.overridePopAnimations(), navigatorExtras)

        private fun NavOptions.overridePopAnimations() =
            NavOptions.Builder()
                .apply {
                    setPopEnterAnim(popEnterAnim.orDefault(R.animator.nav_default_pop_enter_anim))
                    setPopExitAnim(popExitAnim.orDefault(R.animator.nav_default_pop_exit_anim))
                    setEnterAnim(enterAnim)
                    setExitAnim(exitAnim)
                    setLaunchSingleTop(shouldLaunchSingleTop())
                    setPopUpTo(popUpToId, isPopUpToInclusive())
                }
                .build()

        private companion object {
            fun Int.orDefault(@AnimatorRes defaultAnimator: Int): Int =
                if (this != -1) this else defaultAnimator
        }
    }
}
