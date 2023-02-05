// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.ServerStats
import org.equeim.tremotesf.databinding.ServerStatsDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted


class ServerStatsDialogFragment : NavigationDialogFragment() {
    private var binding: ServerStatsDialogBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalRpc.isConnected.combine(GlobalRpc.serverStats, ::Pair).launchAndCollectWhenStarted(this, ::update)
        GlobalRpc.gotDownloadDirFreeSpaceEvents.launchAndCollectWhenStarted(this) {
            binding?.freeSpaceInDownloadDirectoryTextView?.text = FormatUtils.formatByteSize(requireContext(), it)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())

        val binding = ServerStatsDialogBinding.inflate(LayoutInflater.from(builder.context))
        this.binding = binding

        return builder.setTitle(R.string.server_stats)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null).create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun update(viewUpdateData: Pair<Boolean, ServerStats>) {
        val (isConnected, serverStats) = viewUpdateData

        if (!isConnected) return

        binding?.apply {
            val sessionStats = serverStats.currentSession
            sessionDownloadedTextView.text = FormatUtils.formatByteSize(
                requireContext(),
                sessionStats.downloaded()
            )
            sessionUploadedTextView.text = FormatUtils.formatByteSize(
                requireContext(),
                sessionStats.uploaded()
            )
            sessionRatioTextView.text = DecimalFormats.ratio.format(
                sessionStats.uploaded().toDouble() /
                        sessionStats.downloaded().toDouble()
            )
            sessionDurationTextView.text =
                FormatUtils.formatDuration(requireContext(), sessionStats.duration())

            val totalStats = serverStats.total
            val sessionCount = totalStats.sessionCount()
            startedTimedTextView.text = resources.getQuantityString(
                R.plurals.started_times,
                sessionCount,
                sessionCount
            )
            totalDownloadedTextView.text =
                FormatUtils.formatByteSize(requireContext(), totalStats.downloaded())
            totalUploadedTextView.text =
                FormatUtils.formatByteSize(requireContext(), totalStats.uploaded())
            totalRatioTextView.text = DecimalFormats.ratio.format(
                totalStats.uploaded().toDouble() /
                        totalStats.downloaded().toDouble()
            )
            totalDurationTextView.text =
                FormatUtils.formatDuration(requireContext(), totalStats.duration())

            GlobalRpc.nativeInstance.getDownloadDirFreeSpace()
        }
    }
}
