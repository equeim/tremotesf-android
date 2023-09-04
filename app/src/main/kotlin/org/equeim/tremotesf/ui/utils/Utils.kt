// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.service.ForegroundService
import org.equeim.tremotesf.ui.NavigationActivity
import timber.log.Timber


object Utils {
    fun shutdownApp(context: Context, stopService: Boolean = true) {
        Timber.i("Utils.shutdownApp()")
        NavigationActivity.finishAllActivities()
        GlobalRpcClient.disconnectOnShutdown()
        if (stopService) {
            ForegroundService.stop(context)
        }
    }

    fun shareTorrents(magnetLinks: List<String>, context: Context) {
        shareText(magnetLinks.joinToString("\n"), context.getText(R.string.share_torrent), context)
    }

    fun shareText(text: CharSequence, activityChooserTitle: CharSequence, context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ClipDescription.MIMETYPE_TEXT_PLAIN
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivityChooser(intent, activityChooserTitle, context)
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

