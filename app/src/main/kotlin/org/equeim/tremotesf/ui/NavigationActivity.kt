// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.annotation.AnimatorRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.NavigationActivityBinding
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.utils.hideKeyboard
import timber.log.Timber


class NavigationActivity : AppCompatActivity() {
    companion object {
        private val createdActivities = mutableListOf<NavigationActivity>()

        fun finishAllActivities() = createdActivities.apply {
            forEach(Activity::finishAndRemoveTask)
            clear()
        }
    }

    private val model by viewModels<NavigationActivityViewModel>()

    private lateinit var binding: NavigationActivityBinding

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate() called with: savedInstanceState = $savedInstanceState")
        Timber.i("onCreate: intent = $intent")

        // https://issuetracker.google.com/issues/342919181
        @OptIn(PredictiveBackControl::class)
        FragmentManager.enablePredictiveBack(false)

        super.onCreate(savedInstanceState)
        createdActivities.add(this)
        AppForegroundTracker.registerActivity(this)

        applyColorTheme()
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

        Timber.i("onCreate: return")
    }

    private fun applyColorTheme() {
        val colorTheme = ActivityThemeProvider.colorTheme.value
        if (colorTheme == Settings.ColorTheme.System) {
            Timber.i("Applying dynamic colors")
            DynamicColors.applyToActivityIfAvailable(this@NavigationActivity)
        } else {
            Timber.i("Setting color theme $colorTheme")
            setTheme(colorTheme.activityThemeResId)
        }

        lifecycleScope.launch {
            val newColorTheme = ActivityThemeProvider.colorTheme.first { it != colorTheme }
            Timber.i("Color theme changed to $newColorTheme, recreating activity")
            recreate()
        }
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
                    setPopEnterAnim(popEnterAnim.orDefault(androidx.navigation.ui.R.animator.nav_default_pop_enter_anim))
                    setPopExitAnim(popExitAnim.orDefault(androidx.navigation.ui.R.animator.nav_default_pop_exit_anim))
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
