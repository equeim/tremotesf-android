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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
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
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.NON_NEGATIVE_INTEGERS_RANGE
import org.equeim.tremotesf.ui.components.TremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfNumberInputField
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.UNSIGNED_16BIT_RANGE
import org.equeim.tremotesf.ui.components.rememberTremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import kotlin.time.Duration.Companion.minutes

class QueueFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<QueueFragmentViewModel>()
        ServerSettingsQueueScreen(
            settingsRequestState = model.settings.collectAsStateWithLifecycle(),
            navigateUp = navController::navigateUp,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            downloadQueueEnabled = model.downloadQueueEnabled,
            downloadQueueSize = model.downloadQueueSize,
            seedQueueEnabled = model.seedQueueEnabled,
            seedQueueSize = model.seedQueueSize,
            ignoreQueueIfIdle = model.ignoreQueueIfIdle,
            ignoreQueueIfIdleFor = model.ignoreQueueIfIdleFor,
            backgroundRpcRequestsErrors = GlobalRpcClient.backgroundRpcRequestsErrors
        )
    }
}

class QueueFragmentViewModel(application: Application) : AndroidViewModel(application) {
    val settings: StateFlow<RpcRequestState<Any>> =
        GlobalRpcClient.performRecoveringRequest { getQueueServerSettings() }
            .onEach { if (it is RpcRequestState.Loaded) setInitialState(it.response) }
            .stateIn(GlobalRpcClient, viewModelScope)

    val downloadQueueEnabled: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setDownloadQueueEnabled)

    val downloadQueueSize: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState(RpcClient::setDownloadQueueSize)

    val seedQueueEnabled: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setSeedQueueEnabled)

    val seedQueueSize: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState(RpcClient::setSeedQueueSize)

    val ignoreQueueIfIdle: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setIgnoreQueueIfIdle)

    val ignoreQueueIfIdleFor: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState { setIgnoreQueueIfIdleFor(it.minutes) }

    private fun setInitialState(settings: QueueServerSettings) {
        downloadQueueEnabled.reset(settings.downloadQueueEnabled)
        downloadQueueSize.reset(settings.downloadQueueSize)
        seedQueueEnabled.reset(settings.seedQueueEnabled)
        seedQueueSize.reset(settings.seedQueueSize)
        ignoreQueueIfIdle.reset(settings.ignoreQueueIfIdle)
        ignoreQueueIfIdleFor.reset(settings.ignoreQueueIfIdleFor.inWholeMinutes)
    }
}

@Composable
private fun ServerSettingsQueueScreen(
    settingsRequestState: State<RpcRequestState<Any>>,
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    downloadQueueEnabled: ServerSettingsProperty<Boolean>,
    downloadQueueSize: TremotesfIntegerNumberInputFieldState,
    seedQueueEnabled: ServerSettingsProperty<Boolean>,
    seedQueueSize: TremotesfIntegerNumberInputFieldState,
    ignoreQueueIfIdle: ServerSettingsProperty<Boolean>,
    ignoreQueueIfIdleFor: TremotesfIntegerNumberInputFieldState,
    backgroundRpcRequestsErrors: ReceiveChannel<GlobalRpcClient.BackgroundRpcRequestError>
) {
    ServerSettingsCategory(
        title = R.string.server_settings_queue,
        settingsRequestState = settingsRequestState,
        navigateUp = navigateUp,
        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog,
        backgroundRpcRequestsErrors = backgroundRpcRequestsErrors
    ) { horizontalPadding ->
        TremotesfSwitchWithText(
            checked = downloadQueueEnabled.value,
            text = R.string.maximum_active_downloads,
            onCheckedChange = downloadQueueEnabled::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = downloadQueueSize,
            range = NON_NEGATIVE_INTEGERS_RANGE,
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            enabled = downloadQueueEnabled.value
        )
        TremotesfSwitchWithText(
            checked = seedQueueEnabled.value,
            text = R.string.maximum_active_uploads,
            onCheckedChange = downloadQueueEnabled::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = seedQueueSize,
            range = NON_NEGATIVE_INTEGERS_RANGE,
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            enabled = seedQueueEnabled.value
        )
        TremotesfSwitchWithText(
            checked = ignoreQueueIfIdle.value,
            text = R.string.ignore_queue,
            onCheckedChange = ignoreQueueIfIdle::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = ignoreQueueIfIdleFor,
            // Transmission processes this value as unsigned 16-bit integer
            range = UNSIGNED_16BIT_RANGE,
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            enabled = ignoreQueueIfIdle.value,
            suffix = R.string.text_field_suffix_minutes
        )
    }
}

@Preview
@Composable
private fun ServerSettingsQueueScreenPreview() = ScreenPreview {
    ServerSettingsQueueScreen(
        settingsRequestState = remember { mutableStateOf(RpcRequestState.Loaded(Unit)) },
        navigateUp = {},
        navigateToDetailedErrorDialog = {},
        downloadQueueEnabled = remember { ServerSettingsBooleanProperty {} },
        downloadQueueSize = rememberTremotesfIntegerNumberInputFieldState(),
        seedQueueEnabled = remember { ServerSettingsBooleanProperty {} },
        seedQueueSize = rememberTremotesfIntegerNumberInputFieldState(),
        ignoreQueueIfIdle = remember { ServerSettingsBooleanProperty {} },
        ignoreQueueIfIdleFor = rememberTremotesfIntegerNumberInputFieldState(),
        backgroundRpcRequestsErrors = remember { Channel() }
    )
}
