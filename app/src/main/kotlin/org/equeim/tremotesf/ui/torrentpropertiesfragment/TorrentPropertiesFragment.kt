// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.torrentproperties.Peer
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentDetails
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Pause
import org.equeim.tremotesf.ui.ShowRpcErrorsSnackbar
import org.equeim.tremotesf.ui.TorrentRenameDialog
import org.equeim.tremotesf.ui.TorrentsRemoveDialog
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltipAndMenu
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfScrollableTopAppBar
import org.equeim.tremotesf.ui.components.TremotesfTopAppBarDefaults
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.safeNavigate


class TorrentPropertiesFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val args = TorrentPropertiesFragmentArgs.fromBundle(requireArguments())
        val model = viewModel {
            TorrentPropertiesFragmentViewModel(
                torrentHashString = args.torrentHashString,
                savedStateHandle = createSavedStateHandle()
            )
        }
        if (model.shouldNavigateUp) {
            LaunchedEffect(null) { navController.navigateUp() }
        }
        TorrentPropertiesScreen(
            navigateUp = navController::navigateUp,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            navigateToLabelsEditDialog = { enabledLabels ->
                navController.safeNavigate(
                    TorrentPropertiesFragmentDirections.toLabelsEditDialog(
                        torrentHashStrings = arrayOf(args.torrentHashString),
                        enabledLabels = enabledLabels.toTypedArray()
                    )
                )
            },
            navigateToSetLocationDialog = { location ->
                navController.safeNavigate(
                    TorrentPropertiesFragmentDirections.toTorrentsSetLocationDialog(
                        torrentHashStrings = arrayOf(args.torrentHashString),
                        location = location
                    )
                )
            },

            torrentDetails = model.torrentDetails.collectAsStateWithLifecycle(),
            shouldShowLabels = model.shouldShowLabels.collectAsStateWithLifecycle(),

            torrentOperations = model.torrentOperations,

            filesTree = model.filesTree,
            filesTreeState = model.filesTreeState,

            trackers = model.trackers,
            peers = model.peers,
            webSeeders = model.webSeeders,
            limits = model.limits,
            torrentLimitsOperations = model.torrentLimitsOperations,

            quickReturnEnabled = model.quickReturnEnabled::value,

            backgroundRpcRequestsErrors = GlobalRpcClient.backgroundRpcRequestsErrors
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentPropertiesScreen(
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    navigateToLabelsEditDialog: (enabledLabels: List<String>) -> Unit,
    navigateToSetLocationDialog: (location: String) -> Unit,

    torrentDetails: State<RpcRequestState<TorrentDetails>>,
    shouldShowLabels: State<Boolean>,

    torrentOperations: TorrentOperations,

    filesTree: TorrentFilesTree,
    filesTreeState: StateFlow<TorrentPropertiesFragmentViewModel.FilesTreeState>,

    trackers: StateFlow<RpcRequestState<List<TorrentPropertiesFragmentViewModel.TrackerItem>>>,
    peers: StateFlow<RpcRequestState<List<Peer>>>,
    webSeeders: StateFlow<RpcRequestState<List<String>>>,
    limits: StateFlow<RpcRequestState<TorrentLimits>>,
    torrentLimitsOperations: TorrentLimitsOperations,

    quickReturnEnabled: () -> Boolean,

    backgroundRpcRequestsErrors: ReceiveChannel<GlobalRpcClient.BackgroundRpcRequestError>
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pagerState = rememberPagerState { Tab.entries.size }

    val navigateToLabelsEditDialog = {
        val torrentDetails = (torrentDetails.value as? RpcRequestState.Loaded)?.response
        if (torrentDetails != null) {
            navigateToLabelsEditDialog(torrentDetails.labels)
        }
    }

    val navigateToSetLocationDialog = {
        val torrentDetails = (torrentDetails.value as? RpcRequestState.Loaded)?.response
        if (torrentDetails != null) {
            navigateToSetLocationDialog(torrentDetails.downloadDirectory.toNativeSeparators())
        }
    }

    val toolbarClicked = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val snackbarHostState = remember { SnackbarHostState() }
    ShowRpcErrorsSnackbar(snackbarHostState, backgroundRpcRequestsErrors, navigateToDetailedErrorDialog)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                val loadedTorrentDetails = remember {
                    derivedStateOf { (torrentDetails.value as? RpcRequestState.Loaded)?.response }
                }
                val loaded = remember { derivedStateOf { loadedTorrentDetails.value != null } }
                val title = remember { derivedStateOf { loadedTorrentDetails.value?.name } }
                TremotesfScrollableTopAppBar(
                    title = title.value.orEmpty(),
                    scrollBehavior = scrollBehavior,
                    navigateUp = navigateUp,
                    modifier = Modifier.clickable(interactionSource = null, indication = null) {
                        if (quickReturnEnabled()) {
                            toolbarClicked.tryEmit(Unit)
                        }
                    }
                ) {
                    if (loaded.value) {
                        TopAppBarMenuActions(
                            torrentDetails = loadedTorrentDetails,
                            shouldShowLabels = shouldShowLabels,
                            torrentOperations = torrentOperations,
                            navigateToLabelsEditDialog = navigateToLabelsEditDialog,
                            navigateToSetLocationDialog = navigateToSetLocationDialog
                        )
                    }
                }
                if (loaded.value) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = TremotesfTopAppBarDefaults.containerColor(),
                        divider = {}
                    ) {
                        val coroutineScope = rememberCoroutineScope()
                        for (tab in Tab.entries) {
                            Tab(
                                selected = pagerState.currentPage == tab.ordinal,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = TopAppBarDefaults.topAppBarColors().titleContentColor,
                                text = {
                                    Text(
                                        stringResource(
                                            when (tab) {
                                                Tab.Details -> R.string.details
                                                Tab.Files -> R.string.files
                                                Tab.Trackers -> R.string.trackers
                                                Tab.Peers -> R.string.peers
                                                Tab.WebSeeders -> R.string.web_seeders
                                                Tab.Limits -> R.string.limits
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier
            .imePadding()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        TremotesfScreenContentWithPlaceholder(
            requestState = torrentDetails.value,
            onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            placeholdersModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.screenContentPadding())
        ) { torrentDetails ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
            ) { currentPage ->
                val currentTab = Tab.entries[currentPage]
                when (currentTab) {
                    Tab.Details -> DetailsTab(
                        innerPadding = innerPadding,
                        torrentDetails = torrentDetails,
                        shouldShowLabels = shouldShowLabels,
                        navigateToLabelsEditDialog = navigateToLabelsEditDialog
                    )

                    Tab.Files -> FilesTab(
                        innerPadding = innerPadding,
                        filesTree = filesTree,
                        filesTreeState = filesTreeState,
                        toolbarClicked = toolbarClicked,
                        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog
                    )

                    Tab.Trackers -> TrackersTab(
                        innerPadding = innerPadding,
                        trackers = trackers,
                        toolbarClicked = toolbarClicked,
                        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog,
                        torrentOperations = torrentOperations
                    )

                    Tab.Peers -> PeersTab(
                        innerPadding = innerPadding,
                        peers = peers,
                        toolbarClicked = toolbarClicked,
                        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog
                    )

                    Tab.WebSeeders -> WebSeedersTab(
                        innerPadding = innerPadding,
                        webSeeders = webSeeders,
                        toolbarClicked = toolbarClicked,
                        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog
                    )

                    Tab.Limits -> LimitsTab(
                        innerPadding = innerPadding,
                        limits = limits,
                        operations = torrentLimitsOperations,
                        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun TopAppBarMenuActions(
    torrentDetails: State<TorrentDetails?>,
    shouldShowLabels: State<Boolean>,
    torrentOperations: TorrentOperations,
    navigateToLabelsEditDialog: () -> Unit,
    navigateToSetLocationDialog: () -> Unit
) {
    val paused = remember {
        derivedStateOf { torrentDetails.value?.status == TorrentStatus.Paused }
    }
    if (paused.value) {
        TremotesfIconButtonWithTooltip(Icons.Filled.PlayArrow, R.string.start) {
            torrentOperations.start()
        }
    } else {
        TremotesfIconButtonWithTooltip(Icons.Filled.Pause, R.string.pause) {
            torrentOperations.pause()
        }
    }

    val context = LocalContext.current
    TremotesfIconButtonWithTooltip(Icons.Filled.Share, R.string.share) {
        torrentDetails.value?.magnetLink?.let { Utils.shareTorrents(listOf(it), context) }
    }

    var showRenameDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    var showRemoveDialog: Boolean by rememberSaveable { mutableStateOf(false) }

    TremotesfIconButtonWithTooltipAndMenu(Icons.Filled.MoreVert, R.string.more_options) {
        if (paused.value) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.start_now)) },
                onClick = {
                    torrentOperations.startNow()
                    dismiss()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.check_local_data)) },
            onClick = {
                torrentOperations.check()
                dismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reannounce)) },
            onClick = {
                torrentOperations.reannounce()
                dismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.set_location)) },
            onClick = {
                navigateToSetLocationDialog()
                dismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename)) },
            onClick = {
                showRenameDialog = true
                dismiss()
            }
        )
        if (shouldShowLabels.value) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_labels)) },
                onClick = {
                    navigateToLabelsEditDialog()
                    dismiss()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.remove)) },
            onClick = {
                showRemoveDialog = true
                dismiss()
            }
        )
    }

    if (showRenameDialog) {
        val torrentName = torrentDetails.value?.name
        if (torrentName != null) {
            TorrentRenameDialog(
                torrentName = torrentName,
                rename = torrentOperations::rename,
                onDismissRequest = { showRenameDialog = false }
            )
        }
    }

    if (showRemoveDialog) {
        val hashString = torrentDetails.value?.hashString
        if (hashString != null) {
            TorrentsRemoveDialog(
                torrentHashStrings = listOf(hashString),
                removeTorrents = { _, deleteFiles ->
                    torrentOperations.remove(deleteFiles)
                    showRemoveDialog = false
                },
                onDismissRequest = { showRemoveDialog = false }
            )
        }
    }
}

private enum class Tab {
    Details,
    Files,
    Trackers,
    Peers,
    WebSeeders,
    Limits
}
