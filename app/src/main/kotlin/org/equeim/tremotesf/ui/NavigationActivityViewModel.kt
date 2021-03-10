/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.NavMainDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Servers
import org.equeim.tremotesf.ui.addtorrent.AddTorrentLinkFragment
import org.equeim.tremotesf.ui.utils.savedState
import java.util.concurrent.TimeUnit

class NavigationActivityViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    var navigatedInitially by savedState(savedStateHandle) { false }

    fun getInitialNavigationDirections(activity: NavigationActivity, intent: Intent): Pair<NavDirections, NavOptions>? {
        val navOptionsBuilder = lazy {
            NavOptions.Builder()
                .setPopEnterAnim(R.animator.nav_default_pop_enter_anim)
                .setPopExitAnim(R.animator.nav_default_pop_exit_anim)
        }

        var directions = getAddTorrentDirections(activity, intent, navOptionsBuilder)
        if (directions == null) {
            if (!Servers.hasServers) {
                directions = NavMainDirections.toServerEditFragment()
            } else if (shouldShowDonateDialog()) {
                directions = NavMainDirections.toDonateDialog()
            } else {
                return null
            }
        }

        return directions to navOptionsBuilder.value.build()
    }

    private fun getAddTorrentDirections(activity: NavigationActivity, intent: Intent, navOptionsBuilder: Lazy<NavOptions.Builder>?): NavDirections? {
        if (intent.action != Intent.ACTION_VIEW) {
            return null
        }
        val directions = when (intent.scheme) {
            ContentResolver.SCHEME_FILE,
            ContentResolver.SCHEME_CONTENT -> NavMainDirections.toAddTorrentFileFragment(intent.data!!)
            AddTorrentLinkFragment.SCHEME_MAGNET -> NavMainDirections.toAddTorrentLinkFragment(intent.data)
            else -> return null
        }
        if (navOptionsBuilder != null && !activity.isTaskRoot) {
            navOptionsBuilder.value.setPopUpTo(activity.navController.graph.startDestination, true)
        }
        return directions
    }

    fun getAddTorrentDirections(activity: NavigationActivity, intent: Intent): NavDirections? {
        return getAddTorrentDirections(activity, intent, null)
    }

    private fun shouldShowDonateDialog(): Boolean {
        if (Settings.donateDialogShown) {
            return false
        }
        val info = getApplication<Application>().packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
        val currentTime = System.currentTimeMillis()
        val installDays = TimeUnit.DAYS.convert(currentTime - info.firstInstallTime,
            TimeUnit.MILLISECONDS
        )
        val updateDays = TimeUnit.DAYS.convert(currentTime - info.lastUpdateTime,
            TimeUnit.MILLISECONDS
        )
        return (installDays >= 2 && updateDays >= 1)
    }
}