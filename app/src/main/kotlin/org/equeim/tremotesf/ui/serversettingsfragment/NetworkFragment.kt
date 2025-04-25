// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import org.equeim.tremotesf.databinding.ServerSettingsNetworkFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.serversettings.NetworkServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.getNetworkServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setEncryptionMode
import org.equeim.tremotesf.rpc.requests.serversettings.setMaximumPeersGlobally
import org.equeim.tremotesf.rpc.requests.serversettings.setMaximumPeersPerTorrent
import org.equeim.tremotesf.rpc.requests.serversettings.setPeerPort
import org.equeim.tremotesf.rpc.requests.serversettings.setUseDHT
import org.equeim.tremotesf.rpc.requests.serversettings.setUseLPD
import org.equeim.tremotesf.rpc.requests.serversettings.setUsePEX
import org.equeim.tremotesf.rpc.requests.serversettings.setUsePortForwarding
import org.equeim.tremotesf.rpc.requests.serversettings.setUseRandomPort
import org.equeim.tremotesf.rpc.requests.serversettings.setUseUTP
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.handleNumberRangeError
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


class NetworkFragment : NavigationFragment(
    R.layout.server_settings_network_fragment,
    R.string.server_settings_network
) {
    private companion object {
        // Should match R.array.encryption_items
        val encryptionItems = arrayOf(
            NetworkServerSettings.EncryptionMode.Allowed,
            NetworkServerSettings.EncryptionMode.Preferred,
            NetworkServerSettings.EncryptionMode.Required
        )
    }

    private val model by viewModels<NetworkFragmentViewModel>()
    private val binding by viewLifecycleObject(ServerSettingsNetworkFragmentBinding::bind)

    private lateinit var encryptionItemValues: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        encryptionItemValues = resources.getStringArray(R.array.encryption_items)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(binding) {
            peerPortEdit.handleNumberRangeError(Server.portRange) { port ->
                onValueChanged { setPeerPort(port) }
            }

            randomPortCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUseRandomPort(checked) }
            }

            portForwardingCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUsePortForwarding(checked) }
            }

            encryptionView.setAdapter(ArrayDropdownAdapter(encryptionItemValues))
            encryptionView.setOnItemClickListener { _, _, position, _ ->
                onValueChanged { setEncryptionMode(encryptionItems[position]) }
            }

            utpCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUseUTP(checked) }
            }

            pexCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUsePEX(checked) }
            }

            dhtCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUseDHT(checked) }
            }

            lpdCheckBox.setOnCheckedChangeListener { _, checked ->
                onValueChanged { setUseLPD(checked) }
            }

            peersPerTorrentEdit.handleNumberRangeError(0..10000) { limit ->
                onValueChanged { setMaximumPeersPerTorrent(limit) }
            }

            peersGloballyEdit.handleNumberRangeError(0..10000) { limit ->
                onValueChanged { setMaximumPeersGlobally(limit) }
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

    private fun showSettings(settings: NetworkServerSettings) {
        with(binding) {
            scrollView.isVisible = true
            placeholderView.hide()
        }
        if (model.shouldSetInitialState) {
            updateViews(settings)
            model.shouldSetInitialState = false
        }
    }

    private fun updateViews(settings: NetworkServerSettings) = with(binding) {
        peerPortEdit.setText(settings.peerPort.toString())
        randomPortCheckBox.isChecked = settings.useRandomPort
        portForwardingCheckBox.isChecked = settings.usePortForwarding
        encryptionView.setText(encryptionItemValues[encryptionItems.indexOf(settings.encryptionMode)])
        utpCheckBox.isChecked = settings.useUTP
        pexCheckBox.isChecked = settings.usePEX
        dhtCheckBox.isChecked = settings.useDHT
        lpdCheckBox.isChecked = settings.useLPD
        peersPerTorrentEdit.setText(settings.maximumPeersPerTorrent.toString())
        peersGloballyEdit.setText(settings.maximumPeersGlobally.toString())
    }

    private fun onValueChanged(performRpcRequest: suspend RpcClient.() -> Unit) {
        if (!model.shouldSetInitialState) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error, performRpcRequest)
        }
    }
}

class NetworkFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val settings: StateFlow<RpcRequestState<NetworkServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getNetworkServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
