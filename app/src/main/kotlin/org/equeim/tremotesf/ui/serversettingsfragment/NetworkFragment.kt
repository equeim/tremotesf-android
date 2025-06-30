// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequestIntoStateFlow
import org.equeim.tremotesf.rpc.requests.serversettings.NetworkServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.NetworkServerSettings.EncryptionMode
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
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfNumberInputField
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.UNSIGNED_16BIT_RANGE
import org.equeim.tremotesf.ui.components.rememberTremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog


class NetworkFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<NetworkFragmentViewModel>()
        ServerSettingsNetworkScreen(
            settingsRequestState = model.settings.collectAsStateWithLifecycle(),
            navigateUp = navController::navigateUp,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            peerPort = model.peerPort,
            useRandomPort = model.useRandomPort,
            usePortForwarding = model.usePortForwarding,
            encryptionMode = model.encryptionMode,
            useUTP = model.useUTP,
            usePEX = model.usePEX,
            useDHT = model.useDHT,
            useLPD = model.useLPD,
            maximumPeersPerTorrent = model.maximumPeersPerTorrent,
            maximumPeersGlobally = model.maximumPeersGlobally,
            backgroundRpcRequestsErrors = GlobalRpcClient.backgroundRpcRequestsErrors
        )
    }
}


@Composable
private fun ServerSettingsNetworkScreen(
    settingsRequestState: State<RpcRequestState<Any>>,
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    peerPort: TremotesfIntegerNumberInputFieldState,
    useRandomPort: ServerSettingsProperty<Boolean>,
    usePortForwarding: ServerSettingsProperty<Boolean>,
    encryptionMode: ServerSettingsProperty<EncryptionMode>,
    useUTP: ServerSettingsProperty<Boolean>,
    usePEX: ServerSettingsProperty<Boolean>,
    useDHT: ServerSettingsProperty<Boolean>,
    useLPD: ServerSettingsProperty<Boolean>,
    maximumPeersPerTorrent: TremotesfIntegerNumberInputFieldState,
    maximumPeersGlobally: TremotesfIntegerNumberInputFieldState,
    backgroundRpcRequestsErrors: ReceiveChannel<GlobalRpcClient.BackgroundRpcRequestError>
) {
    ServerSettingsCategory(
        title = R.string.server_settings_network,
        settingsRequestState = settingsRequestState,
        navigateUp = navigateUp,
        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog,
        backgroundRpcRequestsErrors = backgroundRpcRequestsErrors
    ) { horizontalPadding ->
        TremotesfSectionHeader(R.string.connection, modifier = Modifier.padding(horizontal = horizontalPadding))
        TremotesfNumberInputField(
            state = peerPort,
            // Transmission processes this value as unsigned 16-bit integer
            range = UNSIGNED_16BIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            label = R.string.peer_port
        )
        TremotesfSwitchWithText(
            checked = useRandomPort.value,
            text = R.string.random_port,
            onCheckedChange = useRandomPort::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = usePortForwarding.value,
            text = R.string.port_forwarding,
            onCheckedChange = usePortForwarding::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )

        TremotesfComboBox(
            currentItem = encryptionMode::value,
            updateCurrentItem = encryptionMode::update,
            items = EncryptionMode.entries,
            itemDisplayString = {
                stringResource(
                    when (it) {
                        EncryptionMode.Allowed -> R.string.encryption_mode_allowed
                        EncryptionMode.Preferred -> R.string.encryption_mode_preferred
                        EncryptionMode.Required -> R.string.encryption_mode_required
                    }
                )
            },
            label = R.string.encryption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )

        TremotesfSwitchWithText(
            checked = useUTP.value,
            text = R.string.enable_utp,
            onCheckedChange = useUTP::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = usePEX.value,
            text = R.string.enable_pex,
            onCheckedChange = usePEX::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = useDHT.value,
            text = R.string.enable_dht,
            onCheckedChange = useDHT::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = useLPD.value,
            text = R.string.enable_lpd,
            onCheckedChange = useLPD::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )

        TremotesfSectionHeader(R.string.peer_limits, modifier = Modifier.padding(horizontal = horizontalPadding))

        TremotesfNumberInputField(
            state = maximumPeersPerTorrent,
            // Transmission processes this value as unsigned 16-bit integer
            range = UNSIGNED_16BIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            label = R.string.maximum_peers_per_torrent
        )

        TremotesfNumberInputField(
            state = maximumPeersGlobally,
            // Transmission processes this value as unsigned 16-bit integer
            range = UNSIGNED_16BIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            label = R.string.maximum_peers_globally
        )
    }
}

@Preview
@Composable
private fun ServerSettingsNetworkScreenPreview() = ScreenPreview {
    ServerSettingsNetworkScreen(
        settingsRequestState = remember { mutableStateOf(RpcRequestState.Loaded(Unit)) },
        navigateUp = {},
        navigateToDetailedErrorDialog = {},
        peerPort = rememberTremotesfIntegerNumberInputFieldState(),
        useRandomPort = remember { ServerSettingsBooleanProperty {} },
        usePortForwarding = remember { ServerSettingsBooleanProperty {} },
        encryptionMode = remember { ServerSettingsProperty(EncryptionMode.Required) {} },
        useUTP = remember { ServerSettingsBooleanProperty {} },
        usePEX = remember { ServerSettingsBooleanProperty {} },
        useDHT = remember { ServerSettingsBooleanProperty {} },
        useLPD = remember { ServerSettingsBooleanProperty {} },
        maximumPeersPerTorrent = rememberTremotesfIntegerNumberInputFieldState(),
        maximumPeersGlobally = rememberTremotesfIntegerNumberInputFieldState(),
        backgroundRpcRequestsErrors = remember { Channel() }
    )
}

class NetworkFragmentViewModel(application: Application) : AndroidViewModel(application) {
    val settings: StateFlow<RpcRequestState<Any>> =
        GlobalRpcClient.performRecoveringRequestIntoStateFlow(viewModelScope) { setInitialState(getNetworkServerSettings()) }

    val peerPort: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState(RpcClient::setPeerPort)

    val useRandomPort: ServerSettingsProperty<Boolean> = ServerSettingsBooleanProperty(RpcClient::setUseRandomPort)

    val usePortForwarding: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setUsePortForwarding)

    val encryptionMode: ServerSettingsProperty<EncryptionMode> =
        ServerSettingsProperty(EncryptionMode.Required, RpcClient::setEncryptionMode)

    val useUTP: ServerSettingsProperty<Boolean> = ServerSettingsBooleanProperty(RpcClient::setUseUTP)
    val usePEX: ServerSettingsProperty<Boolean> = ServerSettingsBooleanProperty(RpcClient::setUsePEX)
    val useDHT: ServerSettingsProperty<Boolean> = ServerSettingsBooleanProperty(RpcClient::setUseDHT)
    val useLPD: ServerSettingsProperty<Boolean> = ServerSettingsBooleanProperty(RpcClient::setUseLPD)

    val maximumPeersPerTorrent: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState(RpcClient::setMaximumPeersPerTorrent)

    val maximumPeersGlobally: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState(RpcClient::setMaximumPeersGlobally)

    private fun setInitialState(settings: NetworkServerSettings) {
        peerPort.reset(settings.peerPort)
        useRandomPort.reset(settings.useRandomPort)
        usePortForwarding.reset(settings.usePortForwarding)
        encryptionMode.reset(settings.encryptionMode)
        useUTP.reset(settings.useUTP)
        usePEX.reset(settings.usePEX)
        useDHT.reset(settings.useDHT)
        useLPD.reset(settings.useLPD)
        maximumPeersPerTorrent.reset(settings.maximumPeersPerTorrent)
        maximumPeersGlobally.reset(settings.maximumPeersGlobally)
    }
}
