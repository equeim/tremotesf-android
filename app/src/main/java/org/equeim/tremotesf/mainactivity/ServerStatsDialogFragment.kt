/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.mainactivity

import java.text.DecimalFormat

import android.app.Dialog
import android.app.DialogFragment

import android.os.Bundle
import android.widget.TextView

import android.support.v7.app.AlertDialog
import android.view.View

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils


class ServerStatsDialogFragment : DialogFragment() {
    private var rpcUpdatedListener: (() -> Unit)? = null
    private val rpcStatusListener = { status: Rpc.Status ->
        if (Rpc.connected) {
            rpcUpdatedListener!!()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.server_stats)
                .setView(R.layout.server_stats_dialog)
                .setPositiveButton(R.string.close, null).create()

        dialog.setOnShowListener {
            val sessionDownloadedTextView = dialog.findViewById(R.id.session_downloaded_text_view) as TextView
            val sessionUploadedTextView = dialog.findViewById(R.id.session_uploaded_text_view) as TextView
            val sessionRatioTextView = dialog.findViewById(R.id.session_ratio_text_view) as TextView
            val sessionDurationTextView = dialog.findViewById(R.id.session_duration_text_view) as TextView

            val startedTimesTextView = dialog.findViewById(R.id.started_timed_text_view) as TextView
            val totalDownloadedTextView = dialog.findViewById(R.id.total_downloaded_text_view) as TextView
            val totalUploadedTextView = dialog.findViewById(R.id.total_uploaded_text_view) as TextView
            val totalRatioTextView = dialog.findViewById(R.id.total_ratio_text_view) as TextView
            val totalDurationTextView = dialog.findViewById(R.id.total_duration_text_view) as TextView

            val ratioFormat = DecimalFormat("0.00")

            val update = {
                val sessionStats = Rpc.serverStats.currentSession
                sessionDownloadedTextView.text = Utils.formatByteSize(activity,
                                                                      sessionStats.downloaded)
                sessionUploadedTextView.text = Utils.formatByteSize(activity,
                                                                    sessionStats.uploaded)
                sessionRatioTextView.text = ratioFormat.format(sessionStats.uploaded.toDouble() /
                                                               sessionStats.downloaded.toDouble())
                sessionDurationTextView.text = Utils.formatDuration(activity, sessionStats.duration)

                val totalStats = Rpc.serverStats.total
                val sessionCount = totalStats.sessionCount
                startedTimesTextView.text = resources.getQuantityString(R.plurals.started_times,
                                                                        sessionCount,
                                                                        sessionCount)
                totalDownloadedTextView.text = Utils.formatByteSize(activity,
                                                                    totalStats.downloaded)
                totalUploadedTextView.text = Utils.formatByteSize(activity, totalStats.uploaded)
                totalRatioTextView.text = ratioFormat.format(totalStats.uploaded.toDouble() /
                                                             totalStats.downloaded.toDouble())
                totalDurationTextView.text = Utils.formatDuration(activity, totalStats.duration)
            }

            update()
            rpcUpdatedListener = update
            Rpc.addUpdatedListener(rpcUpdatedListener!!)
            Rpc.addStatusListener(rpcStatusListener)
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Rpc.removeUpdatedListener(rpcUpdatedListener!!)
        Rpc.removeStatusListener(rpcStatusListener)
        rpcUpdatedListener = null
    }
}