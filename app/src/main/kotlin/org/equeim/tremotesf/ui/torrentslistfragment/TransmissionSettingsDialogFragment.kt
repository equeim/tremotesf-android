package org.equeim.tremotesf.ui.torrentslistfragment

import android.os.Bundle
import android.view.View
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.combine
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TransmissionSettingsDialogFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.NavigationBottomSheetDialogFragment
import org.equeim.tremotesf.ui.utils.collectWhenStarted
import org.equeim.tremotesf.ui.utils.setChildrenEnabled

class TransmissionSettingsDialogFragment :
    NavigationBottomSheetDialogFragment(R.layout.transmission_settings_dialog_fragment) {

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val binding = TransmissionSettingsDialogFragmentBinding.bind(requireView())

        with(binding) {
            connectButton.setOnClickListener {
                if (GlobalRpc.connectionState.value == RpcConnectionState.Disconnected) {
                    GlobalRpc.nativeInstance.connect()
                } else {
                    GlobalRpc.nativeInstance.disconnect()
                }
            }
            combine(GlobalServers.servers, GlobalRpc.connectionState, ::Pair).collectWhenStarted(
                viewLifecycleOwner
            ) { (servers, connectionState) ->
                connectButton.apply {
                    isEnabled = servers.isNotEmpty()
                    text = when (connectionState) {
                        RpcConnectionState.Disconnected -> getString(R.string.connect)
                        RpcConnectionState.Connecting,
                        RpcConnectionState.Connected -> getString(R.string.disconnect)
                        else -> ""
                    }
                }
            }

            val serversViewAdapter = ServersViewAdapter(serversView)
            serversView.apply {
                setAdapter(serversViewAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    serversViewAdapter.servers[position].let {
                        if (it != GlobalServers.currentServer.value) {
                            GlobalServers.setCurrentServer(it)
                        }
                    }
                }
            }
            combine(GlobalServers.servers, GlobalServers.currentServer) { servers, _ -> servers }
                .collectWhenStarted(viewLifecycleOwner) { servers ->
                    serversView.isEnabled = servers.isNotEmpty()
                    serversViewAdapter.update()
                }

            connectionSettings.setOnClickListener {
                navigate(TransmissionSettingsDialogFragmentDirections.toConnectionSettingsFragment())
            }

            serverSettings.setOnClickListener {
                navigate(TransmissionSettingsDialogFragmentDirections.toServerSettingsFragment())
            }

            alternativeLimitsCheckBox.isChecked =
                GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled
            alternativeLimitsClickable.setOnClickListener {
                alternativeLimitsCheckBox.apply {
                    isChecked = !isChecked
                    GlobalRpc.serverSettings.alternativeSpeedLimitsEnabled = isChecked
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

            GlobalRpc.isConnected.collectWhenStarted(viewLifecycleOwner) { connected ->
                listOf(
                    serverSettings,
                    alternativeLimitsClickable,
                    alternativeLimitsCheckBox,
                    serverStats
                ).forEach {
                    it.isEnabled = connected
                }
            }
        }
    }
}