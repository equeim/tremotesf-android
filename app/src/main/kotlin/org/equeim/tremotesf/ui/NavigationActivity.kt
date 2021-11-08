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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.AnimatorRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import kotlinx.coroutines.flow.map
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.NavigationActivityBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.hideKeyboard
import timber.log.Timber


class NavigationActivity : AppCompatActivity(), NavControllerProvider {
    companion object {
        private val createdActivities = mutableListOf<NavigationActivity>()

        private var startedActivity: NavigationActivity? = null

        fun recreateAllActivities() {
            for (activity in createdActivities) {
                activity.recreate()
            }
        }

        fun finishAllActivities() = createdActivities.apply {
            forEach(Activity::finishAndRemoveTask)
            clear()
        }
    }

    private val model by viewModels<NavigationActivityViewModel>()

    private lateinit var binding: NavigationActivityBinding

    var actionMode: ActionMode? = null
        private set

    override lateinit var navController: NavController

    lateinit var appBarConfiguration: AppBarConfiguration
        private set

    lateinit var upNavigationIcon: DrawerArrowDrawable
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate() called with: savedInstanceState = $savedInstanceState")
        Timber.i("onCreate: intent = $intent")

        AppCompatDelegate.setDefaultNightMode(Settings.nightMode)
        setTheme(Settings.theme)

        super.onCreate(savedInstanceState)

        overrideIntentWithDeepLink()

        binding = NavigationActivityBinding.inflate(LayoutInflater.from(this))

        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.apply {
                if (marginLeft != systemBars.left || marginRight != systemBars.right) {
                    updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        leftMargin = systemBars.left
                        rightMargin = systemBars.right
                    }
                }
            }
            insets
        }

        createdActivities.add(this)
        if (Settings.showPersistentNotification) {
            ContextCompat.startForegroundService(this, Intent(this, ForegroundService::class.java))
        }
        GlobalRpc.error.map { it.errorMessage }.launchAndCollectWhenStarted(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            hideKeyboard()
        }

        appBarConfiguration = AppBarConfiguration(navController.graph)
        upNavigationIcon = DrawerArrowDrawable(this).apply { progress = 1.0f }

        Timber.i("onCreate: return")
    }

    private fun overrideIntentWithDeepLink() {
        if (model.navigatedInitially) return
        model.navigatedInitially = true

        val intent = model.getInitialDeepLinkIntent(intent) ?: return
        Timber.i("overrideIntentWithDeepLink: intent = $intent")
        this.intent = intent
    }

    override fun onStart() {
        Timber.i("onStart() called")
        super.onStart()
        if (startedActivity == null) {
            AppForegroundTracker.hasStartedActivity.value = true
        }
        startedActivity = this
    }

    override fun onStop() {
        Timber.i("onStop() called")
        if (!isChangingConfigurations) {
            if (startedActivity === this) {
                startedActivity = null
                AppForegroundTracker.hasStartedActivity.value = false
            }
        }
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

    override fun onSupportActionModeStarted(mode: ActionMode) {
        super.onSupportActionModeStarted(mode)
        actionMode = mode
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        super.onSupportActionModeFinished(mode)
        actionMode = null
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if ((supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.primaryNavigationFragment as? NavigationFragment)?.showOverflowMenu() == true) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}

@Keep
class NavHostFragment : NavHostFragment() {
    override fun onCreateNavController(navController: NavController) {
        super.onCreateNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            Timber.i("Destination changed: destination = $destination, arguments = $arguments")
        }
    }

    override fun createFragmentNavigator(): Navigator<out androidx.navigation.fragment.FragmentNavigator.Destination> {
        return FragmentNavigator(requireContext(), childFragmentManager, id, navController)
    }

    // NavController doesn't set any pop animations when handling deep links
    // Use this workaround to always set pop animations
    @Navigator.Name("fragment")
    class FragmentNavigator(
        context: Context,
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        private val navController: NavController
    ) : androidx.navigation.fragment.FragmentNavigator(context, fragmentManager, containerId) {
        override fun navigate(
            destination: Destination,
            args: Bundle?,
            navOptions: NavOptions?,
            navigatorExtras: Navigator.Extras?
        ): NavDestination? {
            val options = NavOptions.Builder()
                .apply {
                    if (navController.currentDestination != null) {
                        setPopEnterAnim(navOptions?.popEnterAnim.orDefault(R.animator.nav_default_pop_enter_anim))
                        setPopExitAnim(navOptions?.popExitAnim.orDefault(R.animator.nav_default_pop_exit_anim))
                    }
                    if (navOptions != null) {
                        setEnterAnim(navOptions.enterAnim)
                        setExitAnim(navOptions.exitAnim)
                        setLaunchSingleTop(navOptions.shouldLaunchSingleTop())
                        setPopUpTo(navOptions.popUpToId, navOptions.isPopUpToInclusive())
                    }
                }
                .build()
            return super.navigate(destination, args, options, navigatorExtras)
        }

        private fun Int?.orDefault(@AnimatorRes defaultAnimator: Int): Int = when (this) {
            null, -1 -> defaultAnimator
            else -> this
        }
    }
}
