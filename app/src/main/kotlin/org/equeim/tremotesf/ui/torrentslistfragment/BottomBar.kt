// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.FilterList
import org.equeim.tremotesf.ui.TransmissionSettingsIcon
import org.equeim.tremotesf.ui.addtorrent.TORRENT_FILE_MIME_TYPE
import org.equeim.tremotesf.ui.components.TremotesfFloatingActionButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.SortAndFilterSettings
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBar(
    scrollBehaviour: BottomAppBarScrollBehavior,

    shouldConnectToServer: State<Boolean>,
    setShouldConnectToServer: (Boolean) -> Unit,
    currentServer: State<String?>,
    setCurrentServer: (String) -> Unit,
    servers: State<List<String>>,
    alternativeSpeedLimitsEnabled: StateFlow<RpcRequestState<Boolean>>,
    setAlternativeSpeedLimitsEnabled: (Boolean) -> Unit,

    sortAndFilterSettings: State<SortAndFilterSettings?>,
    labelsEnabled: State<Boolean>,
    showTransmissionSettingsButton: State<Boolean>,
    showFiltersAndSearchButtons: State<Boolean>,
    floatingActionButtonState: State<TorrentsListFragmentViewModel.FloatingActionButtonState>,

    navigateToServerAddingScreen: () -> Unit,
    navigateToAddTorrentFileScreen: (Uri) -> Unit,
    navigateToAddTorrentLinkScreen: () -> Unit,
    navigateToConnectionSettingsScreen: () -> Unit,
    navigateToServerSettingsScreen: () -> Unit,
    navigateToServerStatsDialog: () -> Unit,

    allTorrents: State<List<Torrent>>
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        scrollBehavior = scrollBehaviour
    ) {
        var showSearchBar: Boolean by rememberSaveable { mutableStateOf(false) }

        val cancelSearch = {
            showSearchBar = false
            sortAndFilterSettings.value?.nameFilter?.value = ""
        }
        BackHandler(enabled = showSearchBar) {
            cancelSearch()
        }

        var showTransmissionSettingsBottomSheet: Boolean by rememberSaveable { mutableStateOf(false) }
        var showFiltersBottomSheet: Boolean by rememberSaveable { mutableStateOf(false) }
        var showAddTorrentBottomSheet: Boolean by rememberSaveable { mutableStateOf(false) }

        AnimatedContent(showSearchBar) { showSearchBarState ->
            if (showSearchBarState) {
                sortAndFilterSettings.value?.let {
                    SearchBar(sortAndFilterSettings = it, cancelSearch = cancelSearch)
                }
            } else {
                Buttons(
                    showTransmissionSettingsButton = showTransmissionSettingsButton,
                    showFiltersAndSearchButtons = showFiltersAndSearchButtons,
                    sortAndFilterSettings = sortAndFilterSettings,
                    floatingActionButtonState = floatingActionButtonState,
                    setShouldConnectToServer = setShouldConnectToServer,
                    showTransmissionSettingsBottomSheet = { showTransmissionSettingsBottomSheet = true },
                    showFiltersBottomSheet = { showFiltersBottomSheet = true },
                    showSearchBar = { showSearchBar = true },
                    showAddTorrentBottomSheet = { showAddTorrentBottomSheet = true },
                    navigateToServerAddingScreen = navigateToServerAddingScreen
                )
            }
        }

        if (showTransmissionSettingsBottomSheet) {
            TransmissionSettingsBottomSheet(
                shouldConnectToServer = shouldConnectToServer,
                setShouldConnectToServer = setShouldConnectToServer,
                currentServer = currentServer,
                setCurrentServer = setCurrentServer,
                servers = servers,
                alternativeSpeedLimitsEnabled = alternativeSpeedLimitsEnabled,
                setAlternativeSpeedLimitsEnabled = setAlternativeSpeedLimitsEnabled,
                navigateToConnectionSettingsScreen = navigateToConnectionSettingsScreen,
                navigateToServerSettingsScreen = navigateToServerSettingsScreen,
                navigateToServerStatsDialog = navigateToServerStatsDialog,
                onDismissRequest = { showTransmissionSettingsBottomSheet = false }
            )
        }

        if (showFiltersBottomSheet) {
            sortAndFilterSettings.value?.let {
                FiltersBottomSheet(
                    onDismissRequest = { showFiltersBottomSheet = false },
                    sortAndFilterSettings = it,
                    labelsEnabled = labelsEnabled,
                    allTorrents = allTorrents
                )
            }
        }

        val openTorrentFileActivityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let(navigateToAddTorrentFileScreen)
        }
        val launchActivityToOpenTorrentFile = {
            try {
                openTorrentFileActivityLauncher.launch(TORRENT_FILE_MIME_TYPE)
            } catch (e: ActivityNotFoundException) {
                Timber.Forest.e(e, "Failed to start activity")
            }
        }

        if (showAddTorrentBottomSheet) {
            val coroutineScope = rememberCoroutineScope()
            val sheetState = rememberModalBottomSheetState()
            val hide = {
                coroutineScope.launch {
                    try {
                        sheetState.hide()
                    } finally {
                        showAddTorrentBottomSheet = false
                    }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { showAddTorrentBottomSheet = false },
                sheetState = sheetState
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.add_torrent_file)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Companion.Transparent),
                        modifier = Modifier.clickable {
                            launchActivityToOpenTorrentFile()
                            hide()
                        }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.add_torrent_link)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Companion.Transparent),
                        modifier = Modifier.clickable {
                            navigateToAddTorrentLinkScreen()
                            hide()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Buttons(
    showTransmissionSettingsButton: State<Boolean>,
    showFiltersAndSearchButtons: State<Boolean>,
    sortAndFilterSettings: State<SortAndFilterSettings?>,
    floatingActionButtonState: State<TorrentsListFragmentViewModel.FloatingActionButtonState>,
    setShouldConnectToServer: (Boolean) -> Unit,
    showTransmissionSettingsBottomSheet: () -> Unit,
    showFiltersBottomSheet: () -> Unit,
    showSearchBar: () -> Unit,
    showAddTorrentBottomSheet: () -> Unit,
    navigateToServerAddingScreen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.SpacingSmall),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Companion.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            if (showTransmissionSettingsButton.value) {
                TremotesfIconButtonWithTooltip(
                    icon = TransmissionSettingsIcon,
                    textId = R.string.transmission_settings
                ) { showTransmissionSettingsBottomSheet() }
            }
            if (showFiltersAndSearchButtons.value) {
                Box {
                    TremotesfIconButtonWithTooltip(
                        icon = Icons.Filled.FilterList,
                        textId = R.string.torrents_filters
                    ) { showFiltersBottomSheet() }
                    val showBadge = sortAndFilterSettings.value?.isAnySettingChanged?.collectAsStateWithLifecycle(false)
                    if (showBadge != null && showBadge.value) {
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 13.dp, end = 10.dp)
                                .size(7.dp)
                        ) {
                            drawCircle(Color.Red)
                        }
                    }
                }
                TremotesfIconButtonWithTooltip(
                    icon = Icons.Filled.Search,
                    textId = R.string.search
                ) { showSearchBar() }
            }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .padding(Dimens.SpacingSmall),
            contentAlignment = Alignment.Companion.Center,
        ) {
            val fabState = floatingActionButtonState.value
            if (fabState == TorrentsListFragmentViewModel.FloatingActionButtonState.AddTorrent) {
                TremotesfFloatingActionButtonWithTooltip(
                    icon = Icons.Filled.Add,
                    textId = R.string.add_torrent,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                ) {
                    showAddTorrentBottomSheet()
                }
            } else {
                ExtendedFloatingActionButton(
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    onClick = {
                        when (fabState) {
                            TorrentsListFragmentViewModel.FloatingActionButtonState.Connect ->
                                setShouldConnectToServer(true)

                            TorrentsListFragmentViewModel.FloatingActionButtonState.Disconnect ->
                                setShouldConnectToServer(false)

                            TorrentsListFragmentViewModel.FloatingActionButtonState.AddServer ->
                                navigateToServerAddingScreen()

                            // Impossible case
                            TorrentsListFragmentViewModel.FloatingActionButtonState.AddTorrent ->
                                throw IllegalStateException()
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            when (fabState) {
                                TorrentsListFragmentViewModel.FloatingActionButtonState.Connect -> R.string.connect
                                TorrentsListFragmentViewModel.FloatingActionButtonState.Disconnect -> R.string.disconnect
                                TorrentsListFragmentViewModel.FloatingActionButtonState.AddServer -> R.string.add_server
                                // Impossible case
                                TorrentsListFragmentViewModel.FloatingActionButtonState.AddTorrent -> throw IllegalStateException()
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(sortAndFilterSettings: SortAndFilterSettings, cancelSearch: () -> Unit) {
    val focusRequester = rememberTremotesfInitialFocusRequester()
    TextField(
        value = sortAndFilterSettings.nameFilter.value,
        onValueChange = { sortAndFilterSettings.nameFilter.value = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Companion.None),
        leadingIcon = {
            TremotesfIconButtonWithTooltip(Icons.AutoMirrored.Filled.ArrowBack, R.string.close) {
                cancelSearch()
            }
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = sortAndFilterSettings.nameFilter.value.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                TremotesfIconButtonWithTooltip(Icons.Filled.Clear, R.string.clear) {
                    sortAndFilterSettings.nameFilter.value = ""
                }
            }
        },
        placeholder = { Text(stringResource(R.string.search)) },
        modifier = Modifier
            .padding(horizontal = Dimens.SpacingSmall)
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bottomBarScrollBehavior(): BottomAppBarScrollBehavior {
    val delegate = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    return remember(delegate) {
        object : BottomAppBarScrollBehavior by delegate {
            override val isPinned: Boolean get() = true
        }
    }
}
