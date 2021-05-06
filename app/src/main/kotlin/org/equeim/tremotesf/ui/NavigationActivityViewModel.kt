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
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDeepLinkBuilder
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Servers
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileFragmentArgs
import org.equeim.tremotesf.ui.addtorrent.AddTorrentLinkFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentLinkFragmentArgs
import org.equeim.tremotesf.ui.utils.savedState
import org.equeim.tremotesf.utils.Logger
import java.util.concurrent.TimeUnit

class NavigationActivityViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application), Logger {
    var navigatedInitially by savedState(savedStateHandle, false)

    data class AddTorrentDirections(@IdRes val destinationId: Int, val arguments: Bundle)

    fun getAddTorrentDirections(intent: Intent): AddTorrentDirections? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return when (intent.scheme) {
            in AddTorrentFileFragment.SCHEMES -> AddTorrentDirections(
                R.id.add_torrent_file_fragment,
                AddTorrentFileFragmentArgs(data).toBundle()
            )
            in AddTorrentLinkFragment.SCHEMES -> AddTorrentDirections(
                R.id.add_torrent_link_fragment,
                AddTorrentLinkFragmentArgs(data).toBundle()
            )
            else -> null
        }
    }

    fun getInitialDeepLinkIntent(intent: Intent): Intent? {
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            warn("getInitialDeepLinkIntent: activity was launched from history, return null")
            return null
        }

        var deepLinkIntent: Intent? = getAddTorrentDirections(intent)?.run {
            createDeepLinkIntent(
                destinationId,
                arguments,
                intent
            )
        }
        if (deepLinkIntent == null) {
            if ((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
                if (!GlobalServers.hasServers) {
                    deepLinkIntent = createDeepLinkIntent(R.id.server_edit_fragment, null, intent)
                } else if (shouldShowDonateDialog()) {
                    deepLinkIntent = createDeepLinkIntent(R.id.donate_dialog, null, intent)
                }
            } else {
                info("getInitialDeepLinkIntent: we are not on our own task, return null")
            }
        }
        return deepLinkIntent
    }

    private fun shouldShowDonateDialog(): Boolean {
        if (Settings.donateDialogShown) {
            return false
        }
        val info = getApplication<Application>().packageManager.getPackageInfo(
            BuildConfig.APPLICATION_ID,
            0
        )
        val currentTime = System.currentTimeMillis()
        val installDays = TimeUnit.DAYS.convert(
            currentTime - info.firstInstallTime,
            TimeUnit.MILLISECONDS
        )
        val updateDays = TimeUnit.DAYS.convert(
            currentTime - info.lastUpdateTime,
            TimeUnit.MILLISECONDS
        )
        return (installDays >= 2 && updateDays >= 1)
    }

    private fun createDeepLinkIntent(
        @IdRes destinationId: Int,
        arguments: Bundle?,
        originalIntent: Intent
    ): Intent {
        return NavDeepLinkBuilder(getApplication())
            .setGraph(R.navigation.nav_main)
            .setDestination(destinationId)
            .setArguments(arguments)
            .createTaskStackBuilder()
            .intents
            .single()
            .apply {
                // Restore original intent's flags
                flags = originalIntent.flags
                // Prevent NavController from recreating activity if its intent doesn't have FLAG_ACTIVITY_CLEAR_TASK
                if ((flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0 && (flags and Intent.FLAG_ACTIVITY_CLEAR_TASK) == 0) {
                    warn("createDeepLinkIntent: add FLAG_ACTIVITY_CLEAR_TASK")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            }
    }
}
