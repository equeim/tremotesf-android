/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.os.Bundle

import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.server_stats_dialog.*


class ServerStatsDialogFragment : DialogFragment() {
    private var rpcUpdatedListener: (() -> Unit)? = null
    private val rpcStatusListener = { _: Rpc.Status ->
        if (Rpc.connected) {
            rpcUpdatedListener!!()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(context!!)
                .setTitle(R.string.server_stats)
                .setView(R.layout.server_stats_dialog)
                .setPositiveButton(R.string.close, null).create()

        dialog.setOnShowListener {
            val sessionDownloadedTextView = dialog.session_downloaded_text_view!!
            val sessionUploadedTextView = dialog.session_uploaded_text_view!!
            val sessionRatioTextView = dialog.session_ratio_text_view!!
            val sessionDurationTextView = dialog.session_duration_text_view!!

            val startedTimesTextView = dialog.started_timed_text_view!!
            val totalDownloadedTextView = dialog.total_downloaded_text_view!!
            val totalUploadedTextView = dialog.total_uploaded_text_view
            val totalRatioTextView = dialog.total_ratio_text_view
            val totalDurationTextView = dialog.total_duration_text_view!!

            val ratioFormat = DecimalFormat("0.00")

            val update = {
                val sessionStats = Rpc.serverStats.currentSession
                sessionDownloadedTextView.text = Utils.formatByteSize(context!!,
                                                                      sessionStats.downloaded)
                sessionUploadedTextView.text = Utils.formatByteSize(context!!,
                                                                    sessionStats.uploaded)
                sessionRatioTextView.text = ratioFormat.format(sessionStats.uploaded.toDouble() /
                                                               sessionStats.downloaded.toDouble())
                sessionDurationTextView.text = Utils.formatDuration(context!!, sessionStats.duration)

                val totalStats = Rpc.serverStats.total
                val sessionCount = totalStats.sessionCount
                startedTimesTextView.text = resources.getQuantityString(R.plurals.started_times,
                                                                        sessionCount,
                                                                        sessionCount)
                totalDownloadedTextView.text = Utils.formatByteSize(context!!,
                                                                    totalStats.downloaded)
                totalUploadedTextView.text = Utils.formatByteSize(context!!, totalStats.uploaded)
                totalRatioTextView.text = ratioFormat.format(totalStats.uploaded.toDouble() /
                                                             totalStats.downloaded.toDouble())
                totalDurationTextView.text = Utils.formatDuration(context!!, totalStats.duration)
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