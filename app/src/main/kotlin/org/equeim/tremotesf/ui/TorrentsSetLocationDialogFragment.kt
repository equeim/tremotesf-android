// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.getTorrentsDownloadDirectories
import org.equeim.tremotesf.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentsLocation
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogContent
import org.equeim.tremotesf.ui.components.TremotesfDownloadDirectoryField
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.getInitialAllDownloadDirectories
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.components.updateAllDownloadDirectories
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.utils.SnapshotStateListSaver
import org.equeim.tremotesf.ui.utils.localeChangedEvents

class TorrentsSetLocationDialogFragment : ComposeDialogFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val args = TorrentsSetLocationDialogFragmentArgs.fromBundle(requireArguments())
        val model = viewModel {
            TorrentSetLocationDialogViewModel(
                torrentsHashStrings = args.torrentHashStrings.asList(),
                savedStateHandle = createSavedStateHandle(),
                application = checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
            )
        }
        TorrentSetLocationDialogContent(
            allDownloadDirectoriesRequest = model.allDownloadDirectoriesRequest.collectAsStateWithLifecycle(),
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            initialLocation = args::location,
            allDownloadDirectories = model.allDownloadDirectories,
            setLocation = model::setLocation,
            onDismissRequest = ::dismiss
        )
    }
}

@Composable
private fun TorrentSetLocationDialogContent(
    allDownloadDirectoriesRequest: State<RpcRequestState<Any>>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    initialLocation: () -> String,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    setLocation: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    var location: String by rememberSaveable { mutableStateOf(initialLocation()) }
    var moveFiles: Boolean by rememberSaveable { mutableStateOf(false) }
    val setLocationIfNotBlankAndDismiss = {
        if (location.isNotBlank()) {
            setLocation(location, moveFiles)
            onDismissRequest()
        }
    }
    TremotesfAlertDialogContent(
        text = {
            TremotesfScreenContentWithPlaceholder(
                requestState = allDownloadDirectoriesRequest.value,
                onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
                placeholdersModifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    val focusRequester = rememberTremotesfInitialFocusRequester()
                    TremotesfDownloadDirectoryField(
                        downloadDirectory = location,
                        onDownloadDirectoryChanged = { location = it },
                        allDownloadDirectories = allDownloadDirectories,
                        removeDownloadDirectory = allDownloadDirectories::remove,
                        label = R.string.location,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions { setLocationIfNotBlankAndDismiss() },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                    TremotesfSwitchWithText(
                        checked = moveFiles,
                        onCheckedChange = { moveFiles = it },
                        text = R.string.move_files,
                        horizontalContentPadding = Dimens.SpacingSmall
                    )
                }
            }
        },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
            if (allDownloadDirectoriesRequest.value is RpcRequestState.Loaded) {
                TextButton(onClick = setLocationIfNotBlankAndDismiss, enabled = location.isNotBlank()) {
                    Text(
                        stringResource(android.R.string.ok)
                    )
                }
            }
        }
    )
}

class TorrentSetLocationDialogViewModel(
    private val torrentsHashStrings: List<String>,
    savedStateHandle: SavedStateHandle,
    application: Application
) : ViewModel() {
    @OptIn(SavedStateHandleSaveableApi::class)
    val allDownloadDirectories by savedStateHandle.saveable<SnapshotStateList<DownloadDirectoryItem>>(
        saver = SnapshotStateListSaver()
    ) { SnapshotStateList() }

    val allDownloadDirectoriesRequest: StateFlow<RpcRequestState<Any>> = GlobalRpcClient.performRecoveringRequest {
        coroutineScope {
            val settings = async { getDownloadingServerSettings() }
            val torrentsDownloadDirectories = async { getTorrentsDownloadDirectories() }
            settings.await().downloadDirectory to torrentsDownloadDirectories.await()
        }
    }
        .onEach {
            if (it is RpcRequestState.Loaded) {
                val (downloadDirectory, torrentsDownloadDirectories) = it.response
                setInitialState(
                    downloadDirectoryFromServerSettings = downloadDirectory,
                    torrentsDownloadDirectories = torrentsDownloadDirectories
                )
            }
        }
        .stateIn(GlobalRpcClient, viewModelScope)

    private var comparator = AlphanumericComparator()

    private var alreadySetInitialState: Boolean by savedStateHandle.saved { false }

    init {
        viewModelScope.launch {
            application.localeChangedEvents().collect {
                comparator = AlphanumericComparator()
                if (alreadySetInitialState) {
                    allDownloadDirectories.sortWith(compareBy(comparator, DownloadDirectoryItem::directory))
                }
            }
        }
    }

    private fun setInitialState(
        downloadDirectoryFromServerSettings: NormalizedRpcPath,
        torrentsDownloadDirectories: Set<NormalizedRpcPath>
    ) {
        if (!alreadySetInitialState) {
            allDownloadDirectories.addAll(
                getInitialAllDownloadDirectories(
                    downloadDirectoryFromServerSettings = downloadDirectoryFromServerSettings,
                    torrentsDownloadDirectories = torrentsDownloadDirectories,
                    comparator = comparator
                )
            )
            alreadySetInitialState = true
        } else {
            val updated = updateAllDownloadDirectories(
                restoredAllDownloadDirectories = allDownloadDirectories,
                downloadDirectoryFromServerSettings = downloadDirectoryFromServerSettings,
                torrentsDownloadDirectories = torrentsDownloadDirectories,
                comparator = comparator
            )
            allDownloadDirectories.clear()
            allDownloadDirectories.addAll(updated)
        }
    }

    fun setLocation(location: String, moveFiles: Boolean) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.torrent_set_location_error) {
            setTorrentsLocation(torrentsHashStrings, location, moveFiles)
        }
    }
}
