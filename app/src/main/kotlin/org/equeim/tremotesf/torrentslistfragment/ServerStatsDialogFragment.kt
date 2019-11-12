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
import androidx.lifecycle.observe
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.server_stats_dialog.*
import org.equeim.tremotesf.RpcStatus


class ServerStatsDialogFragment : DialogFragment() {
    private val ratioFormat = DecimalFormat("0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rpc.status.observe(this) { status ->
            if (status == RpcStatus.Connected) {
                update()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.server_stats)
                .setView(R.layout.server_stats_dialog)
                .setPositiveButton(R.string.close, null).create()

        dialog.setOnShowListener {
            Rpc.addServerStatsUpdatedListener(::update)
        }

        return dialog
    }

    override fun onDestroyView() {
        Rpc.removeServerStatsUpdatedListener(::update)
        super.onDestroyView()
    }

    private fun update() {
        dialog?.apply {
            val sessionDownloadedTextView = session_downloaded_text_view ?: return

            val sessionStats = Rpc.serverStats.currentSession()
            sessionDownloadedTextView.text = Utils.formatByteSize(requireContext(),
                                                                  sessionStats.downloaded())
            session_uploaded_text_view.text = Utils.formatByteSize(requireContext(),
                                                                sessionStats.uploaded())
            session_ratio_text_view.text = ratioFormat.format(sessionStats.uploaded().toDouble() /
                                                                   sessionStats.downloaded().toDouble())
            session_duration_text_view.text = Utils.formatDuration(requireContext(), sessionStats.duration())

            val totalStats = Rpc.serverStats.total()
            val sessionCount = totalStats.sessionCount()
            started_timed_text_view.text = resources.getQuantityString(R.plurals.started_times,
                                                                    sessionCount,
                                                                    sessionCount)
            total_downloaded_text_view.text = Utils.formatByteSize(requireContext(), totalStats.downloaded())
            total_uploaded_text_view.text = Utils.formatByteSize(requireContext(), totalStats.uploaded())
            total_ratio_text_view.text = ratioFormat.format(totalStats.uploaded().toDouble() /
                                                                 totalStats.downloaded().toDouble())
            total_duration_text_view.text = Utils.formatDuration(requireContext(), totalStats.duration())
        }
    }
}