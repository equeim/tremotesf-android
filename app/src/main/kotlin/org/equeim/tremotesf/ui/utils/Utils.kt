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

package org.equeim.tremotesf.ui.utils

import android.content.Context
import android.content.Intent
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.NavigationActivity
import timber.log.Timber


object Utils {
    fun shutdownApp(context: Context, stopService: Boolean = true) {
        Timber.i("Utils.shutdownApp()")
        NavigationActivity.finishAllActivities()
        GlobalRpc.disconnectOnShutdown()
        if (stopService) {
            ForegroundService.stop(context)
        }
    }

    fun shareTorrents(magnetLinks: List<String>, context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, magnetLinks.joinToString("\n"))
        }
        startActivityChooser(intent, context.getText(R.string.share_torrent), context)
    }

    private fun startActivityChooser(intent: Intent, title: CharSequence, context: Context) {
        Timber.i("startActivityChooser() is called with: intent = $intent, title = $title, context = $context")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, title))
        } else {
            Timber.w("startActivityChooser: failed to resolve activity")
        }
    }
}

