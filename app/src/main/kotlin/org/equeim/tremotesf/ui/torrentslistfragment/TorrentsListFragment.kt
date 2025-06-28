// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.isRecoverable
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltipAndMenu
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelper
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.rememberTremotesfRuntimePermissionHelperState
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.FloatingActionButtonState
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import org.equeim.tremotesf.ui.utils.safeNavigate

class TorrentsListFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<TorrentsListFragmentViewModel>()
        val context = LocalContext.current
        TorrentsListScreen(
            title = model.titleState.collectAsStateWithLifecycle(),
            subtitle = model.subtitleState.collectAsStateWithLifecycle(),

            shouldConnectToServer = GlobalRpcClient.shouldConnectToServer.collectAsStateWithLifecycle(),
            setShouldConnectToServer = { GlobalRpcClient.shouldConnectToServer.value = it },
            currentServer = model.currentServer.collectAsStateWithLifecycle(),
            setCurrentServer = GlobalServers::setCurrentServer,
            servers = model.servers.collectAsStateWithLifecycle(),
            alternativeSpeedLimitsEnabled = model.alternativeSpeedLimitsEnabled,
            setAlternativeSpeedLimitsEnabled = model::setAlternativeSpeedLimitsEnabled,

            labelsEnabled = model.labelsEnabled.collectAsStateWithLifecycle(),

            showTransmissionSettingsButton = model.showTransmissionSettingsButton.collectAsStateWithLifecycle(),
            showFiltersAndSearchButtons = model.showFiltersAndSearchButtons.collectAsStateWithLifecycle(),
            sortAndFilterSettings = model.sortAndFilterSettings.collectAsStateWithLifecycle(),
            floatingActionButtonState = model.floatingActionButtonState.collectAsStateWithLifecycle(),

            navigateToSettings = { navController.safeNavigate(TorrentsListFragmentDirections.toSettingsFragment()) },
            navigateToAboutScreen = { navController.safeNavigate(TorrentsListFragmentDirections.toAboutFragment()) },
            shutdownApp = { Utils.shutdownApp(context) },
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            navigateToServerAddingScreen = { navController.safeNavigate(TorrentsListFragmentDirections.toServerEditFragment()) },
            navigateToAddTorrentFileScreen = {
                navController.safeNavigate(
                    TorrentsListFragmentDirections.toAddTorrentFileFragment(
                        it
                    )
                )
            },
            navigateToAddTorrentLinkScreen = { navController.safeNavigate(TorrentsListFragmentDirections.toAddTorrentLinkFragment()) },
            navigateToTorrentPropertiesScreen = {
                navController.safeNavigate(
                    TorrentsListFragmentDirections.toTorrentPropertiesFragment(
                        it
                    )
                )
            },
            navigateToConnectionSettingsScreen = { navController.safeNavigate(TorrentsListFragmentDirections.toConnectionSettingsFragment()) },
            navigateToServerSettingsScreen = { navController.safeNavigate(TorrentsListFragmentDirections.toServerSettingsFragment()) },
            navigateToServerStatsDialog = { navController.safeNavigate(TorrentsListFragmentDirections.toServerStatsDialog()) },
            navigateToSetLocationDialog = { torrentHashStrings, location ->
                navController.safeNavigate(
                    TorrentsListFragmentDirections.toTorrentsSetLocationDialog(
                        torrentHashStrings.toTypedArray(),
                        location
                    )
                )
            },
            navigateToLabelsEditDialog = { torrentHashStrings, enabledLabels ->
                navController.safeNavigate(
                    TorrentsListFragmentDirections.toLabelsEditDialog(
                        torrentHashStrings.toTypedArray(),
                        enabledLabels.toTypedArray()
                    )
                )
            },

            torrents = model.torrents.collectAsStateWithLifecycle(),
            allTorrents = model.allTorrents.collectAsStateWithLifecycle(),
            refreshingManually = model.refreshingManually,
            refreshManually = model::refreshManually,
            listSettings = model.listSettings.collectAsStateWithLifecycle(),
            quickReturnEnabled = model.quickReturnEnabled.collectAsStateWithLifecycle(),
            torrentsOperations = model.torrentOperations,

            checkNotificationPermission = model.checkNotificationPermission,
            onCheckedNotificationPermission = model::onCheckedNotificationPermission,
            onShownNotificationPermissionRequest = model::onShownNotificationPermissionRequest
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TorrentsListScreen(
    title: State<TorrentsListFragmentViewModel.TitleState?>,
    subtitle: State<TorrentsListFragmentViewModel.SubtitleState?>,

    shouldConnectToServer: State<Boolean>,
    setShouldConnectToServer: (Boolean) -> Unit,
    currentServer: State<String?>,
    setCurrentServer: (String) -> Unit,
    servers: State<List<String>>,
    alternativeSpeedLimitsEnabled: StateFlow<RpcRequestState<Boolean>>,
    setAlternativeSpeedLimitsEnabled: (Boolean) -> Unit,

    labelsEnabled: State<Boolean>,

    sortAndFilterSettings: State<TorrentsListFragmentViewModel.SortAndFilterSettings?>,
    showTransmissionSettingsButton: State<Boolean>,
    showFiltersAndSearchButtons: State<Boolean>,
    floatingActionButtonState: State<FloatingActionButtonState>,

    navigateToSettings: () -> Unit,
    navigateToAboutScreen: () -> Unit,
    shutdownApp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    navigateToServerAddingScreen: () -> Unit,
    navigateToAddTorrentFileScreen: (Uri) -> Unit,
    navigateToAddTorrentLinkScreen: () -> Unit,
    navigateToTorrentPropertiesScreen: (String) -> Unit,
    navigateToConnectionSettingsScreen: () -> Unit,
    navigateToServerSettingsScreen: () -> Unit,
    navigateToServerStatsDialog: () -> Unit,
    navigateToSetLocationDialog: (torrentHashStrings: List<String>, location: String) -> Unit,
    navigateToLabelsEditDialog: (torrentHashStrings: List<String>, enabledLabels: List<String>) -> Unit,

    torrents: State<RpcRequestState<List<Torrent>>>,
    allTorrents: State<RpcRequestState<List<Torrent>>>,
    refreshingManually: State<Boolean>,
    refreshManually: () -> Unit,
    listSettings: State<TorrentsListFragmentViewModel.ListSettings?>,
    quickReturnEnabled: State<Boolean>,
    torrentsOperations: TorrentsOperations,

    checkNotificationPermission: StateFlow<Boolean?>,
    onCheckedNotificationPermission: () -> Unit,
    onShownNotificationPermissionRequest: () -> Unit
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val bottomAppBarScrollBehaviour = bottomBarScrollBehavior()

    val toolbarClicked =
        remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val title = title.value
                    Text(
                        text = if (title != null) {
                            stringResource(R.string.current_server_string, title.serverName, title.serverAddress)
                        } else {
                            stringResource(R.string.app_name)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                subtitle = {
                    val subtitle = subtitle.value
                    if (subtitle != null) {
                        val fileSizeFormatter = rememberFileSizeFormatter()
                        Text(
                            stringResource(
                                R.string.main_activity_subtitle,
                                fileSizeFormatter.formatTransferRate(subtitle.downloadSpeed),
                                fileSizeFormatter.formatTransferRate(subtitle.uploadSpeed)
                            )
                        )
                    }
                },
                titleHorizontalAlignment = Alignment.CenterHorizontally,
                actions = {
                    TremotesfIconButtonWithTooltipAndMenu(Icons.Filled.MoreVert, R.string.more_options) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = {
                                navigateToSettings()
                                dismiss()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about)) },
                            onClick = {
                                navigateToAboutScreen()
                                dismiss()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quit)) },
                            onClick = {
                                shutdownApp()
                                dismiss()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                scrollBehavior = topAppBarScrollBehavior,
                modifier = Modifier.clickable(interactionSource = null, indication = null) {
                    if (quickReturnEnabled.value) {
                        toolbarClicked.tryEmit(Unit)
                    }
                }
            )
        },
        bottomBar = {
            BottomBar(
                scrollBehaviour = bottomAppBarScrollBehaviour,

                shouldConnectToServer = shouldConnectToServer,
                setShouldConnectToServer = setShouldConnectToServer,
                currentServer = currentServer,
                setCurrentServer = setCurrentServer,
                servers = servers,
                alternativeSpeedLimitsEnabled = alternativeSpeedLimitsEnabled,
                setAlternativeSpeedLimitsEnabled = setAlternativeSpeedLimitsEnabled,

                sortAndFilterSettings = sortAndFilterSettings,
                labelsEnabled = labelsEnabled,
                showTransmissionSettingsButton = showTransmissionSettingsButton,
                showFiltersAndSearchButtons = showFiltersAndSearchButtons,
                floatingActionButtonState = floatingActionButtonState,

                navigateToServerAddingScreen = navigateToServerAddingScreen,
                navigateToAddTorrentFileScreen = navigateToAddTorrentFileScreen,
                navigateToAddTorrentLinkScreen = navigateToAddTorrentLinkScreen,
                navigateToConnectionSettingsScreen = navigateToConnectionSettingsScreen,
                navigateToServerSettingsScreen = navigateToServerSettingsScreen,
                navigateToServerStatsDialog = navigateToServerStatsDialog,

                allTorrents = remember {
                    derivedStateOf { (allTorrents.value as? RpcRequestState.Loaded)?.response ?: emptyList() }
                }
            )
        },
        modifier = Modifier
            .imePadding()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .nestedScroll(bottomAppBarScrollBehaviour.nestedScrollConnection)
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        val enablePullToRefresh = remember {
            derivedStateOf {
                when (val state = torrents.value) {
                    is RpcRequestState.Loaded -> true
                    is RpcRequestState.Error -> state.error.isRecoverable
                    else -> false
                }
            }
        }
        Box(
            Modifier.pullToRefresh(
                isRefreshing = refreshingManually.value,
                state = pullToRefreshState,
                enabled = enablePullToRefresh.value,
                onRefresh = refreshManually
            )
        ) {
            TremotesfScreenContentWithPlaceholder(
                requestState = torrents.value,
                onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
                loadingText = R.string.connecting,
                modifier = Modifier.consumeWindowInsets(innerPadding),
                placeholdersModifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(Dimens.screenContentPadding())
            ) { torrents ->
                val listSettings = listSettings.value ?: return@TremotesfScreenContentWithPlaceholder
                val compactView = listSettings.compactView.collectAsStateWithLifecycle()
                val multilineName = listSettings.multilineName.collectAsStateWithLifecycle()
                TorrentsList(
                    innerPadding = innerPadding,
                    torrents = torrents,
                    compactView = compactView.value,
                    multilineName = multilineName.value,
                    toolbarClicked = toolbarClicked,
                    labelsEnabled = labelsEnabled,
                    sortAndFilterSettings = sortAndFilterSettings,
                    torrentsOperations = torrentsOperations,
                    navigateToTorrentPropertiesScreen = navigateToTorrentPropertiesScreen,
                    navigateToSetLocationDialog = navigateToSetLocationDialog,
                    navigateToLabelsEditDialog = navigateToLabelsEditDialog
                )
            }
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = refreshingManually.value,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(innerPadding)
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionHelperState = rememberTremotesfRuntimePermissionHelperState(
            requiredPermission = Manifest.permission.POST_NOTIFICATIONS,
            showRationaleBeforeRequesting = false
        )
        TremotesfRuntimePermissionHelper(
            state = notificationPermissionHelperState,
            permissionRationaleText = R.string.notification_permission_rationale
        )
        var showSnackbar: Boolean by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(notificationPermissionHelperState, checkNotificationPermission) {
            if (checkNotificationPermission.filterNotNull().first() && notificationPermissionHelperState.permissionGranted) {
                onCheckedNotificationPermission()
                showSnackbar = true
            }
        }
        if (showSnackbar) {
            val message = stringResource(R.string.notification_permission_rationale)
            val actionLabel = stringResource(R.string.request_permission)
            LaunchedEffect(message, actionLabel) {
                R.string.request_notification_permission
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )
                onShownNotificationPermissionRequest()
                if (result == SnackbarResult.ActionPerformed) {
                    notificationPermissionHelperState.requestPermission()
                }
            }
        }
    }
}
