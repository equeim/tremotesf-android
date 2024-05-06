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
import org.equeim.tremotesf.databinding.ServerSettingsQueueFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.serversettings.QueueServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.getQueueServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadQueueEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadQueueSize
import org.equeim.tremotesf.rpc.requests.serversettings.setIgnoreQueueIfIdle
import org.equeim.tremotesf.rpc.requests.serversettings.setIgnoreQueueIfIdleFor
import org.equeim.tremotesf.rpc.requests.serversettings.setSeedQueueEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setSeedQueueSize
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.handleNumberRangeError
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import kotlin.time.Duration.Companion.minutes


class QueueFragment : NavigationFragment(
    R.layout.server_settings_queue_fragment,
    R.string.server_settings_queue
) {
    private val model by viewModels<QueueFragmentViewModel>()
    private val binding by viewLifecycleObject(ServerSettingsQueueFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(binding) {
            downloadQueueCheckBox.setDependentViews(downloadQueueLayout) { checked ->
                onValueChanged { setDownloadQueueEnabled(checked) }
            }

            downloadQueueEdit.handleNumberRangeError(0..10000) { queueSize ->
                onValueChanged { setDownloadQueueSize(queueSize) }
            }

            seedQueueCheckBox.setDependentViews(seedQueueLayout) { checked ->
                onValueChanged { setSeedQueueEnabled(checked) }
            }

            seedQueueEdit.handleNumberRangeError(0..10000) { queueSize ->
                onValueChanged { setSeedQueueSize(queueSize) }
            }

            idleQueueCheckBox.setDependentViews(idleQueueLayout) { checked ->
                onValueChanged { setIgnoreQueueIfIdle(checked) }
            }

            idleQueueEdit.handleNumberRangeError(0..10000) { limit ->
                onValueChanged { setIgnoreQueueIfIdleFor(limit.minutes) }
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

    private fun showSettings(settings: QueueServerSettings) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        if (model.shouldSetInitialState) {
            updateViews(settings)
            model.shouldSetInitialState = false
        }
    }

    private fun updateViews(settings: QueueServerSettings) = with(binding) {
        downloadQueueCheckBox.isChecked = settings.downloadQueueEnabled
        downloadQueueEdit.setText(settings.downloadQueueSize.toString())
        seedQueueCheckBox.isChecked = settings.seedQueueEnabled
        seedQueueEdit.setText(settings.seedQueueSize.toString())
        idleQueueCheckBox.isChecked = settings.ignoreQueueIfIdle
        idleQueueEdit.setText(settings.ignoreQueueIfIdleFor.inWholeMinutes.toString())
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.() -> Unit) {
        if (!model.shouldSetInitialState) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error, performRpcRequest)
        }
    }
}

class QueueFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val settings: StateFlow<RpcRequestState<QueueServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getQueueServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
