// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
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
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadDirectory
import org.equeim.tremotesf.rpc.requests.serversettings.setIncompleteDirectory
import org.equeim.tremotesf.rpc.requests.serversettings.setIncompleteDirectoryEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setRenameIncompleteFiles
import org.equeim.tremotesf.rpc.requests.serversettings.setStartAddedTorrents
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog

class DownloadingFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<DownloadingFragmentViewModel>()
        ServerSettingsDownloadingScreen(
            settingsRequestState = model.settings.collectAsStateWithLifecycle(),
            navigateUp = navController::navigateUp,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            downloadDirectory = model.downloadDirectory,
            startAddedTorrents = model.startAddedTorrents,
            renameIncompleteTorrents = model.renameIncompleteFiles,
            incompleteDirectoryEnabled = model.incompleteDirectoryEnabled,
            incompleteDirectory = model.incompleteDirectory,
            backgroundRpcRequestsErrors = GlobalRpcClient.backgroundRpcRequestsErrors
        )
    }
}

class DownloadingFragmentViewModel : ViewModel() {
    val settings: StateFlow<RpcRequestState<Any>> =
        GlobalRpcClient.performRecoveringRequestIntoStateFlow(viewModelScope) {
            setInitialState(getDownloadingServerSettings())
        }

    val downloadDirectory: ServerSettingsProperty<String> =
        ServerSettingsStringProperty(RpcClient::setDownloadDirectory)
    val startAddedTorrents: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setStartAddedTorrents)
    val renameIncompleteFiles: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setRenameIncompleteFiles)
    val incompleteDirectoryEnabled: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setIncompleteDirectoryEnabled)
    val incompleteDirectory: ServerSettingsProperty<String> =
        ServerSettingsStringProperty(RpcClient::setIncompleteDirectory)

    private fun setInitialState(settings: DownloadingServerSettings) {
        downloadDirectory.reset(settings.downloadDirectory.toNativeSeparators())
        startAddedTorrents.reset(settings.startAddedTorrents)
        renameIncompleteFiles.reset(settings.renameIncompleteFiles)
        incompleteDirectoryEnabled.reset(settings.incompleteDirectoryEnabled)
        incompleteDirectory.reset(settings.incompleteDirectory.toNativeSeparators())
    }
}

@Composable
private fun ServerSettingsDownloadingScreen(
    settingsRequestState: State<RpcRequestState<Any>>,
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    downloadDirectory: ServerSettingsProperty<String>,
    startAddedTorrents: ServerSettingsProperty<Boolean>,
    renameIncompleteTorrents: ServerSettingsProperty<Boolean>,
    incompleteDirectoryEnabled: ServerSettingsProperty<Boolean>,
    incompleteDirectory: ServerSettingsProperty<String>,
    backgroundRpcRequestsErrors: ReceiveChannel<GlobalRpcClient.BackgroundRpcRequestError>
) {
    ServerSettingsCategory(
        title = R.string.server_settings_downloading,
        settingsRequestState = settingsRequestState,
        navigateUp = navigateUp,
        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog,
        backgroundRpcRequestsErrors = backgroundRpcRequestsErrors
    ) { horizontalPadding ->
        OutlinedTextField(
            value = downloadDirectory.value,
            onValueChange = downloadDirectory::update,
            label = { Text(stringResource(R.string.download_directory)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )
        TremotesfSwitchWithText(
            checked = startAddedTorrents.value,
            text = R.string.start_added_torrents,
            onCheckedChange = startAddedTorrents::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = renameIncompleteTorrents.value,
            text = R.string.rename_incomplete_files,
            onCheckedChange = renameIncompleteTorrents::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfSwitchWithText(
            checked = incompleteDirectoryEnabled.value,
            text = R.string.incomplete_files_directory,
            onCheckedChange = incompleteDirectoryEnabled::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        OutlinedTextField(
            value = incompleteDirectory.value,
            onValueChange = incompleteDirectory::update,
            enabled = incompleteDirectoryEnabled.value,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )
    }
}

@Preview
@Composable
private fun ServerSettingsDownloadingScreenPreview() = ScreenPreview {
    ServerSettingsDownloadingScreen(
        settingsRequestState = remember { mutableStateOf(RpcRequestState.Loaded(Unit)) },
        navigateUp = {},
        navigateToDetailedErrorDialog = {},
        downloadDirectory = remember { ServerSettingsStringProperty {} },
        startAddedTorrents = remember { ServerSettingsBooleanProperty {} },
        renameIncompleteTorrents = remember { ServerSettingsBooleanProperty {} },
        incompleteDirectoryEnabled = remember { ServerSettingsBooleanProperty {} },
        incompleteDirectory = remember { ServerSettingsStringProperty {} },
        backgroundRpcRequestsErrors = remember { Channel() }
    )
}
