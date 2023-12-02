// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerStatsDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.FileSize
import org.equeim.tremotesf.torrentfile.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.torrentfile.rpc.requests.getDownloadDirFreeSpace
import org.equeim.tremotesf.torrentfile.rpc.requests.getSessionStats
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading


class ServerStatsDialogFragment : NavigationDialogFragment() {
    private val viewModel: ServerStatsDialogFragmentViewModel by viewModels()
    private var binding: ServerStatsDialogBinding? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())

        val binding = ServerStatsDialogBinding.inflate(LayoutInflater.from(builder.context))
        this.binding = binding

        viewModel.stats.launchAndCollectWhenStarted(this) {
            when (it) {
                is RpcRequestState.Loaded -> {
                    val (stats, downloadDirFreeSpace) = it.response
                    showStats(stats, downloadDirFreeSpace)
                }
                is RpcRequestState.Loading -> showPlaceholder(null)
                is RpcRequestState.Error -> showPlaceholder(it.error)
            }
        }

        return builder
            .setTitle(R.string.server_stats)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null).create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun showPlaceholder(error: RpcRequestError?) {
        checkNotNull(binding).apply {
            scrollView.isVisible = false
            error?.let(placeholderView::showError) ?: placeholderView.showLoading()
        }
    }

    private fun showStats(stats: SessionStatsResponseArguments, downloadDirFreeSpace: FileSize) {
        checkNotNull(binding).apply {
            scrollView.isVisible = true
            placeholderView.hide()

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

            freeSpaceInDownloadDirectoryTextView.text =
                FormatUtils.formatFileSize(requireContext(), downloadDirFreeSpace)
        }
    }
}

class ServerStatsDialogFragmentViewModel : ViewModel() {
    private val sessionStats: Flow<RpcRequestState<SessionStatsResponseArguments>> =
        GlobalRpcClient.performPeriodicRequest { getSessionStats() }
    private val downloadDirFreeSpace: Flow<RpcRequestState<FileSize>> =
        GlobalRpcClient.performPeriodicRequest { getDownloadDirFreeSpace() }
    val stats: StateFlow<RpcRequestState<Pair<SessionStatsResponseArguments, FileSize>>> =
        combine(sessionStats, downloadDirFreeSpace) { stats, freeSpace ->
            when {
                stats is RpcRequestState.Loaded && freeSpace is RpcRequestState.Loaded -> RpcRequestState.Loaded(stats.response to freeSpace.response)
                stats is RpcRequestState.Error -> stats
                freeSpace is RpcRequestState.Error -> freeSpace
                else -> RpcRequestState.Loading
            }
        }.stateIn(GlobalRpcClient, viewModelScope)
}
