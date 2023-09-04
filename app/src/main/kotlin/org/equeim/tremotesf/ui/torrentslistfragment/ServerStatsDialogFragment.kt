// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.mapNotNull
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerStatsDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.PeriodicServerStateUpdater
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.FileSize
import org.equeim.tremotesf.torrentfile.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.torrentfile.rpc.requests.getDownloadDirFreeSpace
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted


class ServerStatsDialogFragment : NavigationDialogFragment() {
    private var binding: ServerStatsDialogBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())

        val binding = ServerStatsDialogBinding.inflate(LayoutInflater.from(builder.context))
        this.binding = binding

        PeriodicServerStateUpdater.sessionStats
            .mapNotNull { (it as? RpcRequestState.Loaded)?.response }
            .launchAndCollectWhenStarted(this, ::update)
        GlobalRpcClient.performPeriodicRequest { getDownloadDirFreeSpace() }
            .mapNotNull { (it as? RpcRequestState.Loaded)?.response }
            .launchAndCollectWhenStarted(this, ::update)

        return builder.setTitle(R.string.server_stats)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null).create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun update(stats: SessionStatsResponseArguments) {
        checkNotNull(binding).apply {
            val sessionStats = stats.currentSession
            sessionDownloadedTextView.text = FormatUtils.formatFileSize(
                requireContext(),
                sessionStats.downloaded
            )
            sessionUploadedTextView.text = FormatUtils.formatFileSize(
                requireContext(),
                sessionStats.uploaded
            )
            sessionRatioTextView.text = DecimalFormats.ratio.format(
                sessionStats.uploaded.bytes.toDouble() /
                        sessionStats.downloaded.bytes.toDouble()
            )
            sessionDurationTextView.text =
                FormatUtils.formatDuration(requireContext(), sessionStats.active)

            val totalStats = stats.total
            val sessionCount = totalStats.sessionCount
            startedTimedTextView.text = resources.getQuantityString(
                R.plurals.started_times,
                sessionCount,
                sessionCount
            )
            totalDownloadedTextView.text =
                FormatUtils.formatFileSize(requireContext(), totalStats.downloaded)
            totalUploadedTextView.text =
                FormatUtils.formatFileSize(requireContext(), totalStats.uploaded)
            totalRatioTextView.text = DecimalFormats.ratio.format(
                totalStats.uploaded.bytes.toDouble() /
                        totalStats.downloaded.bytes.toDouble()
            )
            totalDurationTextView.text =
                FormatUtils.formatDuration(requireContext(), totalStats.active)
        }
    }

    private fun update(downloadDirFreeSpace: FileSize) {
        checkNotNull(binding).freeSpaceInDownloadDirectoryTextView.text =
            FormatUtils.formatFileSize(requireContext(), downloadDirFreeSpace)
    }
}
