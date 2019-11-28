/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.intentFor

import org.equeim.tremotesf.utils.hideKeyboard

import kotlinx.android.synthetic.main.navigation_activity.*


class NavigationActivity : AppCompatActivity(R.layout.navigation_activity), Selector.ActionModeActivity, AnkoLogger {
    companion object {
        private val createdActivities = mutableListOf<NavigationActivity>()
        var activeActivity: NavigationActivity? = null
            private set

        fun finishAllActivities() {
            for (activity in createdActivities) {
                activity.finish()
            }
            createdActivities.clear()
        }

        fun showToast(text: String) {
            activeActivity?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
        }
    }

    override var actionMode: ActionMode? = null

    lateinit var navController: NavController
        private set

    var drawerSetUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        info("NavigationActivity.onCreate(), intent=$intent")

        AppCompatDelegate.setDefaultNightMode(Settings.nightMode)
        setTheme(Settings.theme)

        super.onCreate(savedInstanceState)

        createdActivities.add(this)
        if (Settings.showPersistentNotification) {
            ContextCompat.startForegroundService(this, intentFor<ForegroundService>())
        }
        Rpc.connectOnce()

        navController = findNavController(R.id.nav_host)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            hideKeyboard()
            if (destination.id == R.id.torrentsListFragment) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            } else {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
            }
        }

        handleAddTorrentIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (activeActivity == null) {
            Rpc.cancelUpdateWorker()
            Rpc.nativeInstance.setUpdateDisabled(false)
        }
        activeActivity = this
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            if (activeActivity === this) {
                activeActivity = null

                if (!Settings.showPersistentNotification) {
                    Servers.save()
                    Rpc.nativeInstance.setUpdateDisabled(true)
                    Rpc.enqueueUpdateWorker()
                }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        info("NavigationActivity.onDestroy()")
        createdActivities.remove(this)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        info("NavigationActivity.onNewIntent(), intent=$intent")
        super.onNewIntent(intent)
        handleAddTorrentIntent(intent)
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun handleAddTorrentIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val action = when (intent.scheme) {
                ContentResolver.SCHEME_FILE,
                ContentResolver.SCHEME_CONTENT -> R.id.action_global_addTorrentFileFragment
                AddTorrentLinkFragment.SCHEME_MAGNET -> R.id.action_global_addTorrentLinkFragment
                else -> return
            }
            val arguments = bundleOf(AddTorrentFragment.URI to intent.data!!.toString())
            if (isTaskRoot) {
                findNavController(R.id.nav_host).navigate(action, arguments)
            } else {
                findNavController(R.id.nav_host).navigate(action, arguments,
                                                          NavOptions.Builder().setPopUpTo(R.id.nav_main, true).build())
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val navHostFragment = (nav_host as NavHostFragment)
        if (navHostFragment.navController.currentDestination?.id == R.id.aboutFragment) {
            (navHostFragment.childFragmentManager.primaryNavigationFragment as? AboutFragment)?.onActivityResult(requestCode, resultCode, data)
        }
        super.onActivityResult(requestCode, resultCode, data)
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
