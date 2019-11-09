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

package org.equeim.tremotesf.torrentslistfragment

import java.text.DecimalFormat

import android.app.Dialog
import android.os.Bundle

import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.server_stats_dialog.*


class ServerStatsDialogFragment : DialogFragment() {
    private var serverStatsUpdatedListener: (() -> Unit)? = null
    private val rpcStatusListener: (Int) -> Unit = {
        if (Rpc.isConnected) {
            serverStatsUpdatedListener?.invoke()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
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
                val sessionStats = Rpc.serverStats.currentSession()
                sessionDownloadedTextView.text = Utils.formatByteSize(requireContext(),
                                                                      sessionStats.downloaded())
                sessionUploadedTextView.text = Utils.formatByteSize(requireContext(),
                                                                    sessionStats.uploaded())
                sessionRatioTextView.text = ratioFormat.format(sessionStats.uploaded().toDouble() /
                                                               sessionStats.downloaded().toDouble())
                sessionDurationTextView.text = Utils.formatDuration(requireContext(), sessionStats.duration())

                val totalStats = Rpc.serverStats.total()
                val sessionCount = totalStats.sessionCount()
                startedTimesTextView.text = resources.getQuantityString(R.plurals.started_times,
                                                                        sessionCount,
                                                                        sessionCount)
                totalDownloadedTextView.text = Utils.formatByteSize(requireContext(), totalStats.downloaded())
                totalUploadedTextView.text = Utils.formatByteSize(requireContext(), totalStats.uploaded())
                totalRatioTextView.text = ratioFormat.format(totalStats.uploaded().toDouble() /
                                                             totalStats.downloaded().toDouble())
                totalDurationTextView.text = Utils.formatDuration(requireContext(), totalStats.duration())
            }

            update()
            serverStatsUpdatedListener = update
            Rpc.addServerStatsUpdatedListener(update)
            Rpc.addStatusListener(rpcStatusListener)
        }

        return dialog
    }

    override fun onDestroyView() {
        serverStatsUpdatedListener?.let { Rpc.removeServerStatsUpdatedListener(it) }
        Rpc.removeStatusListener(rpcStatusListener)
        serverStatsUpdatedListener = null
        super.onDestroyView()
    }
}