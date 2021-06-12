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
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.onNavDestinationSelected
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.NavigationActivityBinding
import org.equeim.tremotesf.databinding.SidePanelHeaderBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.sidepanel.DirectoriesViewAdapter
import org.equeim.tremotesf.ui.sidepanel.ServersViewAdapter
import org.equeim.tremotesf.ui.sidepanel.StatusFilterViewAdapter
import org.equeim.tremotesf.ui.sidepanel.TrackersViewAdapter
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentDirections
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.setChildrenEnabled
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

    lateinit var drawerNavigationIcon: DrawerArrowDrawable
        private set
    lateinit var upNavigationIcon: DrawerArrowDrawable
        private set

    lateinit var sidePanelBinding: SidePanelHeaderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("onCreate() called with: savedInstanceState = $savedInstanceState")
        Timber.i("onCreate: intent = $intent")

        AppCompatDelegate.setDefaultNightMode(Settings.nightMode)
        setTheme(Settings.theme)

        super.onCreate(savedInstanceState)

        overrideIntentWithDeepLink()

        binding = NavigationActivityBinding.inflate(LayoutInflater.from(this))

        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    window.decorView.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }

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
        GlobalRpc.error.map { it.errorMessage }.collectWhenStarted(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }

        drawerNavigationIcon = DrawerArrowDrawable(this)
        upNavigationIcon = DrawerArrowDrawable(this).apply { progress = 1.0f }

        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        appBarConfiguration = AppBarConfiguration(navController.graph, binding.drawerLayout)

        setupDrawer()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            hideKeyboard()
            val lockMode = if (isTopLevelDestination(destination.id)) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
            binding.drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)
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
                    .setPopUpTo(navController.graph.startDestination, false)
                    .build()
            )
        }
    }

    override fun onBackPressed() {
        binding.drawerLayout.apply {
            if (isDrawerOpen(GravityCompat.START)) {
                closeDrawer(GravityCompat.START)
            } else {
                super.onBackPressed()
            }
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

    private fun setupDrawer() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.sidePanel) { view, insets ->
            view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        binding.sidePanel.setNavigationItemSelectedListener { menuItem ->
            return@setNavigationItemSelectedListener when (menuItem.itemId) {
                R.id.quit -> {
                    Utils.shutdownApp(this)
                    true
                }
                else -> menuItem.onNavDestinationSelected(navController)
            }
        }

        val sidePanelHeader = binding.sidePanel.getHeaderView(0)

        with(SidePanelHeaderBinding.bind(sidePanelHeader)) {
            val serversViewAdapter = ServersViewAdapter(serversView)
            serversView.setAdapter(serversViewAdapter)
            serversView.setOnItemClickListener { _, _, position, _ ->
                serversViewAdapter.servers[position].let {
                    if (it != GlobalServers.currentServer.value) {
                        GlobalServers.setCurrentServer(it)
                    }
                }
            }
            combine(GlobalServers.servers, GlobalServers.currentServer) { servers, _ -> servers }
                .collectWhenStarted(this@NavigationActivity) { servers ->
                    serversView.isEnabled = servers.isNotEmpty()
                    serversViewAdapter.update()
                }

            connectionSettingsButton.setOnClickListener {
                navigate(TorrentsListFragmentDirections.toConnectionSettingsFragment())
            }

            GlobalRpc.isConnected.collectWhenStarted(
                this@NavigationActivity,
                listSettingsLayout::setChildrenEnabled
            )

            sortView.setAdapter(ArrayDropdownAdapter(resources.getStringArray(R.array.sort_spinner_items)))
            sortView.setText(sortView.adapter.getItem(Settings.torrentsSortMode.ordinal) as String)
            val resId = if (Settings.torrentsSortOrder == TorrentsListFragmentViewModel.SortOrder.Descending) {
                R.drawable.sort_descending
            } else {
                R.drawable.sort_ascending
            }
            sortViewLayout.setStartIconDrawable(resId)

            statusView.setAdapter(StatusFilterViewAdapter(this@NavigationActivity, statusView))
            trackersView.setAdapter(TrackersViewAdapter(this@NavigationActivity, trackersView))
            directoriesView.setAdapter(
                DirectoriesViewAdapter(
                    this@NavigationActivity,
                    directoriesView
                )
            )

            sidePanelBinding = this
        }
    }

    // destinationId must not refer to NavGraph
    fun isTopLevelDestination(@IdRes destinationId: Int): Boolean {
        return appBarConfiguration.topLevelDestinations.contains(destinationId)
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
                        setPopUpTo(navOptions.popUpTo, navOptions.isPopUpToInclusive)
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
