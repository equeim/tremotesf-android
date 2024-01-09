// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsDownloadingFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadDirectory
import org.equeim.tremotesf.rpc.requests.serversettings.setIncompleteDirectory
import org.equeim.tremotesf.rpc.requests.serversettings.setIncompleteDirectoryEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setRenameIncompleteFiles
import org.equeim.tremotesf.rpc.requests.serversettings.setStartAddedTorrents
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


class DownloadingFragment : NavigationFragment(
    R.layout.server_settings_downloading_fragment,
    R.string.server_settings_downloading
) {
    private val model by viewModels<DownloadingFragmentViewModel>()
    private val binding by viewLifecycleObject(ServerSettingsDownloadingFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(ServerSettingsDownloadingFragmentBinding.bind(requireView())) {
            downloadDirectoryEdit.doAfterTextChangedAndNotEmpty {
                onValueChanged { setDownloadDirectory(it.toString()) }
            }
            startTorrentsCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setStartAddedTorrents(checked) }
            }
            renameIncompleteFilesCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setRenameIncompleteFiles(checked) }
            }
            incompleteFilesDirectoryCheckBox.setDependentViews(incompleteFilesDirectoryLayout) { checked ->
                onValueChanged { setIncompleteDirectoryEnabled(checked) }
            }
            incompleteFilesDirectoryEdit.doAfterTextChangedAndNotEmpty {
                onValueChanged { setIncompleteDirectory(it.toString()) }
            }
        }

        model.settings.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> showSettings(it.response)
                is RpcRequestState.Loading -> showPlaceholder(null)
                is RpcRequestState.Error -> showPlaceholder(it.error)
            }
        }
    }

    private fun showPlaceholder(error: RpcRequestError?) {
        hideKeyboard()
        with(binding) {
            scrollView.isVisible = false
            error?.let(placeholderView::showError) ?: placeholderView.showLoading()
        }
    }

    private fun showSettings(settings: DownloadingServerSettings) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        if (model.shouldSetInitialState) {
            updateViews(settings)
            model.shouldSetInitialState = false
        }
    }

    private fun updateViews(settings: DownloadingServerSettings) = with(binding) {
        downloadDirectoryEdit.setText(settings.downloadDirectory.toNativeSeparators())
        startTorrentsCheckBox.isChecked = settings.startAddedTorrents
        renameIncompleteFilesCheckBox.isChecked = settings.renameIncompleteFiles
        incompleteFilesDirectoryCheckBox.isChecked = settings.incompleteDirectoryEnabled
        incompleteFilesDirectoryEdit.setText(settings.incompleteDirectory.toNativeSeparators())
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.() -> Unit) {
        if (!model.shouldSetInitialState) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error, performRpcRequest)
        }
    }
}

class DownloadingFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val settings: StateFlow<RpcRequestState<DownloadingServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getDownloadingServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
