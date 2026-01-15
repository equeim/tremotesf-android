// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.addtorrent.BaseAddTorrentModel.DownloadDirectoryFreeSpace
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester


class AddTorrentLinkFragment : ComposeFragment() {
    private val args: AddTorrentLinkFragmentArgs by navArgs()

    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel {
            AddTorrentLinkModel(
                args.uris?.asList().orEmpty(),
                createSavedStateHandle(),
                checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY))
            )
        }
        AddTorrentLinkScreen(
            navigateUp = navController::navigateUp,
            initialRpcInputsRequestState = model.initialRpcInputs.collectAsStateWithLifecycle(),
            torrentLinksText = model.torrentLinksText,
            downloadDirectory = model.downloadDirectory,
            allDownloadDirectories = model.allDownloadDirectories,
            downloadDirectoryFreeSpace = model.downloadDirectoryFreeSpace.collectAsStateWithLifecycle(),
            priority = model.priority,
            startAddedTorrents = model.startAddedTorrents,
            enabledLabels = model.enabledLabels,
            allLabels = model.allLabels,
            addTorrentState = model.addTorrentState,
            shouldShowLabels = model.shouldShowLabels.collectAsStateWithLifecycle(),
            showMergingTrackersMessage = model.showMergingTrackersMessage,
            onMergeTrackersDialogResult = model::onMergeTrackersDialogResult,
            addTorrent = model::addTorrent,
            shouldStartDragAndDrop = model::shouldStartDragAndDrop,
            dragAndDropTarget = model
        )
        HandleFinishedAddTorrentState(model.addTorrentState, navController, requireActivity())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTorrentLinkScreen(
    navigateUp: () -> Unit,
    initialRpcInputsRequestState: State<RpcRequestState<*>>,
    torrentLinksText: MutableState<String>,
    downloadDirectory: MutableState<String>,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    downloadDirectoryFreeSpace: State<DownloadDirectoryFreeSpace?>,
    priority: MutableState<TorrentLimits.BandwidthPriority>,
    startAddedTorrents: MutableState<Boolean>,
    enabledLabels: SnapshotStateList<String>,
    allLabels: State<List<String>>,
    shouldShowLabels: State<Boolean>,
    addTorrentState: State<AddTorrentState?>,
    showMergingTrackersMessage: MutableState<MergingTrackersMessage?>,
    onMergeTrackersDialogResult: (MergeTrackersDialogResult) -> Unit,
    addTorrent: () -> Unit,
    shouldStartDragAndDrop: (DragAndDropEvent) -> Boolean,
    dragAndDropTarget: DragAndDropTarget,
) {
    var showTorrentLinkError: Boolean by rememberSaveable { mutableStateOf(false) }
    val showDownloadDirectoryError = rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TremotesfTopAppBar(stringResource(R.string.add_torrent_link), navigateUp) },
        floatingActionButton = {
            val checkingIfTorrentExists: Boolean by remember {
                derivedStateOf { addTorrentState.value is AddTorrentState.CheckingIfTorrentExists }
            }
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.add)) },
                icon = {
                    if (checkingIfTorrentExists) {
                        CircularProgressIndicator()
                    } else {
                        Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.add))
                    }
                },
                expanded = !WindowInsets.isImeVisible,
                onClick = {
                    if (checkingIfTorrentExists) {
                        return@ExtendedFloatingActionButton
                    }
                    if (torrentLinksText.value.isBlank()) {
                        showTorrentLinkError = true
                    }
                    if (downloadDirectory.value.isBlank()) {
                        showDownloadDirectoryError.value = true
                    }
                    if (!showTorrentLinkError && !showDownloadDirectoryError.value) {
                        addTorrent()
                    }
                }
            )
        },
        floatingActionButtonPosition = if (WindowInsets.isImeVisible) {
            FabPosition.End
        } else {
            FabPosition.Center
        },
        modifier = Modifier
            .imePadding()
            .dragAndDropTarget(
                shouldStartDragAndDrop = shouldStartDragAndDrop,
                target = dragAndDropTarget
            )
    ) { innerPadding ->
        TremotesfScreenContentWithPlaceholder(
            requestState = initialRpcInputsRequestState.value,
            modifier = Modifier.consumeWindowInsets(innerPadding),
            placeholdersModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.screenContentPadding()),
            content = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(vertical = Dimens.screenContentPaddingVertical())
                        .padding(bottom = Dimens.PaddingForFAB),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    val horizontalPadding = Dimens.screenContentPaddingHorizontal()

                    val shouldRequestFocus = rememberSaveable { torrentLinksText.value.isEmpty() }
                    val focusRequester = if (shouldRequestFocus) {
                        rememberTremotesfInitialFocusRequester()
                    } else {
                        null
                    }

                    OutlinedTextField(
                        value = torrentLinksText.value,
                        onValueChange = {
                            torrentLinksText.value = it
                            if (it.isNotBlank()) {
                                showTorrentLinkError = false
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.None),
                        label = { Text(stringResource(R.string.torrent_links)) },
                        isError = showTorrentLinkError,
                        supportingText = if (showTorrentLinkError) {
                            { Text(stringResource(R.string.empty_field_error)) }
                        } else {
                            null
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = shouldStartDragAndDrop,
                                target = dragAndDropTarget
                            ).run {
                                if (focusRequester != null) {
                                    focusRequester(focusRequester)
                                } else {
                                    this
                                }
                            }
                    )

                    CommonAddTorrentParameters(
                        downloadDirectory = downloadDirectory,
                        showDownloadDirectoryError = showDownloadDirectoryError,
                        allDownloadDirectories = allDownloadDirectories,
                        downloadDirectoryFreeSpace = downloadDirectoryFreeSpace,
                        priority = priority,
                        startAddedTorrents = startAddedTorrents,
                        enabledLabels = enabledLabels,
                        allLabels = allLabels,
                        shouldShowLabels = shouldShowLabels
                    )
                }
            }
        )
    }

    ShowMergingTrackersMessage(showMergingTrackersMessage, snackbarHostState)

    val showMergingTrackersDialog: AddTorrentState.AskForMergingTrackers? by remember {
        derivedStateOf { addTorrentState.value as? AddTorrentState.AskForMergingTrackers }
    }
    showMergingTrackersDialog?.let {
        MergingTrackersDialog(
            torrentNames = it.torrentNames,
            cancellable = true,
            onMergeTrackersDialogResult = onMergeTrackersDialogResult
        )
    }
}

@Preview
@Composable
private fun AddTorrentLinkScreenPreview() = ScreenPreview {
    AddTorrentLinkScreen(
        navigateUp = {},
        initialRpcInputsRequestState = remember { mutableStateOf(RpcRequestState.Loaded(Unit)) },
        torrentLinksText = remember { mutableStateOf("") },
        downloadDirectory = remember { mutableStateOf("/home/dude") },
        allDownloadDirectories = remember { SnapshotStateList() },
        downloadDirectoryFreeSpace = remember {
            mutableStateOf(DownloadDirectoryFreeSpace.FreeSpace(FileSize.fromBytes(10000000)))
        },
        priority = remember { mutableStateOf(TorrentLimits.BandwidthPriority.Normal) },
        startAddedTorrents = remember { mutableStateOf(true) },
        enabledLabels = remember { mutableStateListOf("egegege", "PLRPLR", "AAAAAAAA") },
        allLabels = remember { mutableStateOf(emptyList()) },
        shouldShowLabels = remember { mutableStateOf(true) },
        addTorrentState = remember { mutableStateOf(null) },
        showMergingTrackersMessage = remember { mutableStateOf(null) },
        onMergeTrackersDialogResult = {},
        addTorrent = {},
        shouldStartDragAndDrop = { false },
        dragAndDropTarget = remember {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent) = false
            }
        }
    )
}
