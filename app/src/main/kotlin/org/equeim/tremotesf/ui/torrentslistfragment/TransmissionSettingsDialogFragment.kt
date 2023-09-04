// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavOptions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TransmissionSettingsDialogFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performRecoveringRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.SpeedServerSettings
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.getSpeedServerSettings
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.setAlternativeLimitsEnabled
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted

class TransmissionSettingsDialogFragment :
    NavigationBottomSheetDialogFragment(R.layout.transmission_settings_dialog_fragment) {

    private val model by viewModels<TransmissionSettingsDialogFragmentViewModel>()

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val binding = TransmissionSettingsDialogFragmentBinding.bind(requireView())

        with(binding) {
            connectButton.setOnClickListener {
                GlobalRpcClient.shouldConnectToServer.value = !GlobalRpcClient.shouldConnectToServer.value
            }
            combine(GlobalServers.hasServers, GlobalRpcClient.shouldConnectToServer, ::Pair).launchAndCollectWhenStarted(
                viewLifecycleOwner
            ) { (hasServers, shouldConnectToServer) ->
                connectButton.apply {
                    isEnabled = hasServers
                    setText(if (shouldConnectToServer) {
                        R.string.disconnect
                    } else {
                        R.string.connect
                    })
                }
            }

            val serversViewAdapter = ServersViewAdapter(serversView)
            serversView.apply {
                setAdapter(serversViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    serversViewAdapter.servers[position].let {
                        GlobalServers.setCurrentServer(it.name)
                    }
                }
            }
            GlobalServers.serversState.launchAndCollectWhenStarted(viewLifecycleOwner) {
                serversView.isEnabled = it.servers.isNotEmpty()
                serversViewAdapter.update()
            }

            connectionSettings.setOnClickListener {
                navigate(TransmissionSettingsDialogFragmentDirections.toConnectionSettingsFragment())
            }

            serverSettings.setOnClickListener {
                navigate(TransmissionSettingsDialogFragmentDirections.toServerSettingsFragment())
            }

            model.speedSettings.launchAndCollectWhenStarted(viewLifecycleOwner) {
                if (it is RpcRequestState.Loaded && model.shouldSetInitialState) {
                    alternativeLimitsCheckBox.isChecked = it.response.alternativeLimitsEnabled
                    model.shouldSetInitialState = false
                }
            }
            alternativeLimitsClickable.setOnClickListener {
                alternativeLimitsCheckBox.apply {
                    val enabled = !isChecked
                    isChecked = enabled
                    GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error) { setAlternativeLimitsEnabled(enabled) }
                }
            }

            serverStats.setOnClickListener {
                navigate(
                    TransmissionSettingsDialogFragmentDirections.toServerStatsDialog(),
                    NavOptions.Builder().setPopUpTo(
                        checkNotNull(navController.previousBackStackEntry).destination.id, false
                    ).build()
                )
            }

            GlobalRpcClient.shouldConnectToServer.launchAndCollectWhenStarted(viewLifecycleOwner) { shouldConnect ->
                listOf(
                    serverSettings,
                    alternativeLimitsClickable,
                    alternativeLimitsCheckBox,
                    serverStats
                ).forEach {
                    it.isEnabled = shouldConnect
                }
            }
        }
    }
}

class TransmissionSettingsDialogFragmentViewModel : ViewModel() {
    var shouldSetInitialState = true
    val speedSettings: StateFlow<RpcRequestState<SpeedServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getSpeedServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialState = true }
            .stateIn(GlobalRpcClient, viewModelScope)
}
