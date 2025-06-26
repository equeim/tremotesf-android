// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileModel.LoadingState
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileModel.LoadingState.FileLoadingError
import org.equeim.tremotesf.ui.addtorrent.BaseAddTorrentModel.DownloadDirectoryFreeSpace
import org.equeim.tremotesf.ui.components.DownloadDirectoryItem
import org.equeim.tremotesf.ui.components.TremotesfErrorPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfLoadingPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelper
import org.equeim.tremotesf.ui.components.TremotesfScrollableTopAppBarWithSubtitle
import org.equeim.tremotesf.ui.components.TremotesfTopAppBarDefaults
import org.equeim.tremotesf.ui.components.TremotesfTorrentsFilesList
import org.equeim.tremotesf.ui.components.rememberTremotesfRuntimePermissionHelperState
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter


class AddTorrentFileFragment : ComposeFragment() {
    private val args: AddTorrentFileFragmentArgs by navArgs()

    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<AddTorrentFileModelImpl> {
            AddTorrentFileModelImpl(
                uri = args.uri,
                application = checkNotNull(get(AndroidViewModelFactory.APPLICATION_KEY)),
                savedStateHandle = createSavedStateHandle()
            )
        }
        val activity = checkNotNull(LocalActivity.current) as ComponentActivity
        AddTorrentFileScreen(
            navigateUp = navController::navigateUp,
            performBackPress = activity.onBackPressedDispatcher::onBackPressed,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,

            needStoragePermission = model.needStoragePermission,

            loadTorrentFile = model::load,
            loadingState = model.loadingState,
            downloadDirectory = model.downloadDirectory,
            allDownloadDirectories = model.allDownloadDirectories,
            downloadDirectoryFreeSpace = model.downloadDirectoryFreeSpace.collectAsStateWithLifecycle(),
            priority = model.priority,
            startAddedTorrents = model.startAddedTorrents,
            enabledLabels = model.enabledLabels,
            allLabels = model.allLabels,
            shouldShowLabels = model.shouldShowLabels.collectAsStateWithLifecycle(),

            filesTree = model.filesTree,

            addTorrent = model::addTorrentFile,
            addTorrentState = model.addTorrentState,
            onMergeTrackersDialogResult = model::onMergeTrackersDialogResult
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTorrentFileScreen(
    navigateUp: () -> Unit,
    performBackPress: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,

    needStoragePermission: Boolean,

    loadTorrentFile: () -> Unit,
    loadingState: State<LoadingState>,

    downloadDirectory: MutableState<String>,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    downloadDirectoryFreeSpace: State<DownloadDirectoryFreeSpace?>,
    priority: MutableState<TorrentLimits.BandwidthPriority>,
    startAddedTorrents: MutableState<Boolean>,
    enabledLabels: SnapshotStateList<String>,
    allLabels: State<List<String>>,
    shouldShowLabels: State<Boolean>,

    filesTree: TorrentFilesTree,

    addTorrent: () -> Unit,
    addTorrentState: State<AddTorrentState?>,
    onMergeTrackersDialogResult: (MergeTrackersDialogResult) -> Unit
) {
    val showDownloadDirectoryError = rememberSaveable { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pagerState = rememberPagerState { Tab.entries.size }

    Scaffold(
        topBar = {
            Column {
                TremotesfScrollableTopAppBarWithSubtitle(
                    title = stringResource(R.string.add_torrent_file),
                    subtitle = (loadingState.value as? LoadingState.Loaded)?.torrentName.orEmpty(),
                    scrollBehavior = scrollBehavior,
                    navigateUp = navigateUp
                )
                if (loadingState.value is LoadingState.Loaded) {
                    PrimaryTabRow(
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
                                                Tab.Info -> R.string.information
                                                Tab.Files -> R.string.files
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
        val permissionHelperState = if (needStoragePermission) {
            val state = rememberTremotesfRuntimePermissionHelperState(
                requiredPermission = Manifest.permission.READ_EXTERNAL_STORAGE,
                showRationaleBeforeRequesting = true
            )
            TremotesfRuntimePermissionHelper(state, R.string.storage_permission_rationale_torrent)
            state
        } else {
            null
        }

        if (permissionHelperState != null && !permissionHelperState.permissionGranted) {
            LaunchedEffect(permissionHelperState) {
                permissionHelperState.requestPermission()
            }

            TremotesfErrorPlaceholder(
                error = stringResource(R.string.storage_permission_error),
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
                    .padding(Dimens.screenContentPadding())
            )
            return@Scaffold
        }

        LaunchedEffect(null) { loadTorrentFile() }

        when (val state = loadingState.value) {
            is LoadingState.Initial, is LoadingState.Aborted -> Unit
            is LoadingState.Loading -> TremotesfLoadingPlaceholder(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
                    .padding(Dimens.screenContentPadding())
            )

            is FileLoadingError -> {
                TremotesfErrorPlaceholder(
                    error = stringResource(
                        when (state) {
                            FileLoadingError.FileIsTooLarge -> R.string.file_is_too_large
                            FileLoadingError.ReadingError -> R.string.file_reading_error
                            FileLoadingError.ParsingError -> R.string.file_parsing_error
                        }
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .padding(innerPadding)
                        .padding(Dimens.screenContentPadding())
                )
            }

            is LoadingState.InitialRpcInputsError -> TremotesfErrorPlaceholder(
                error = state.error,
                onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding)
                    .padding(Dimens.screenContentPadding())
            )

            is LoadingState.Loaded -> LoadedContent(
                innerPadding = innerPadding,
                pagerState = pagerState,

                downloadDirectory = downloadDirectory,
                showDownloadDirectoryError = showDownloadDirectoryError,
                allDownloadDirectories = allDownloadDirectories,
                downloadDirectoryFreeSpace = downloadDirectoryFreeSpace,
                priority = priority,
                startAddedTorrents = startAddedTorrents,
                enabledLabels = enabledLabels,
                allLabels = allLabels,
                shouldShowLabels = shouldShowLabels,

                filesTree = filesTree,

                addTorrent = addTorrent,
                addTorrentState = addTorrentState
            )
        }

        when (loadingState.value) {
            is LoadingState.Loaded, is LoadingState.Aborted -> {
                HandleAddTorrentState(
                    addTorrentState = addTorrentState.value,
                    mergeDialogCancellable = false,
                    onMergeTrackersDialogResult = onMergeTrackersDialogResult,
                    performBackPress = performBackPress
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun LoadedContent(
    innerPadding: PaddingValues,
    pagerState: PagerState,

    downloadDirectory: MutableState<String>,
    showDownloadDirectoryError: MutableState<Boolean>,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    downloadDirectoryFreeSpace: State<DownloadDirectoryFreeSpace?>,
    priority: MutableState<TorrentLimits.BandwidthPriority>,
    startAddedTorrents: MutableState<Boolean>,
    enabledLabels: SnapshotStateList<String>,
    allLabels: State<List<String>>,
    shouldShowLabels: State<Boolean>,

    filesTree: TorrentFilesTree,

    addTorrent: () -> Unit,
    addTorrentState: State<AddTorrentState?>,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding)
    ) { currentPage ->
        val currentTab = Tab.entries[currentPage]
        when (currentTab) {
            Tab.Info -> InfoTab(
                innerPadding = innerPadding,
                downloadDirectory = downloadDirectory,
                showDownloadDirectoryError = showDownloadDirectoryError,
                allDownloadDirectories = allDownloadDirectories,
                downloadDirectoryFreeSpace = downloadDirectoryFreeSpace,
                priority = priority,
                startAddedTorrents = startAddedTorrents,
                enabledLabels = enabledLabels,
                allLabels = allLabels,
                shouldShowLabels = shouldShowLabels,
                addTorrentState = addTorrentState,
                addTorrent = addTorrent
            )

            Tab.Files -> FilesTab(
                filesTree = filesTree,
                innerPadding = innerPadding
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoTab(
    innerPadding: PaddingValues,
    downloadDirectory: MutableState<String>,
    showDownloadDirectoryError: MutableState<Boolean>,
    allDownloadDirectories: SnapshotStateList<DownloadDirectoryItem>,
    downloadDirectoryFreeSpace: State<DownloadDirectoryFreeSpace?>,
    priority: MutableState<TorrentLimits.BandwidthPriority>,
    startAddedTorrents: MutableState<Boolean>,
    enabledLabels: SnapshotStateList<String>,
    allLabels: State<List<String>>,
    shouldShowLabels: State<Boolean>,
    addTorrentState: State<AddTorrentState?>,
    addTorrent: () -> Unit,
) {
    Box {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = Dimens.screenContentPaddingVertical())
                .padding(bottom = Dimens.PaddingForFAB),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
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

        val checkingIfTorrentExists: Boolean by remember {
            derivedStateOf { addTorrentState.value is AddTorrentState.CheckingIfTorrentExists }
        }
        val layoutDirection = LocalLayoutDirection.current
        val fabPadding = PaddingValues(
            end = innerPadding.calculateEndPadding(layoutDirection) + Dimens.SpacingBig,
            bottom = innerPadding.calculateBottomPadding() + Dimens.SpacingBig
        )
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
                if (downloadDirectory.value.isBlank()) {
                    showDownloadDirectoryError.value = true
                }
                if (!showDownloadDirectoryError.value) {
                    addTorrent()
                }
            },
            modifier = Modifier
                .align(
                    if (WindowInsets.isImeVisible) {
                        Alignment.BottomEnd
                    } else {
                        Alignment.BottomCenter
                    }
                )
                .padding(fabPadding)
        )
    }
}


@Composable
private fun FilesTab(
    filesTree: TorrentFilesTree,
    innerPadding: PaddingValues
) {
    val fileSizeFormatter = rememberFileSizeFormatter()
    TremotesfTorrentsFilesList(
        filesTree = filesTree,
        contentPadding = innerPadding,
        itemSupportingContent = { Text(fileSizeFormatter.formatFileSize(FileSize.fromBytes(it.size))) },
        modifier = Modifier.fillMaxSize()
    )
}

private enum class Tab {
    Info,
    Files
}

@Preview
@Composable
private fun AddTorrentFileScreenPreview() = ScreenPreview {
    val coroutineScope = rememberCoroutineScope()

    AddTorrentFileScreen(
        navigateUp = {},
        performBackPress = {},
        navigateToDetailedErrorDialog = {},

        needStoragePermission = false,

        loadTorrentFile = {},
        loadingState = remember { mutableStateOf(LoadingState.Loaded("Cat.")) },

        downloadDirectory = remember { mutableStateOf("") },
        allDownloadDirectories = remember { SnapshotStateList() },
        downloadDirectoryFreeSpace = remember {
            mutableStateOf(DownloadDirectoryFreeSpace.FreeSpace(FileSize.fromBytes(666666)))
        },
        priority = remember { mutableStateOf(TorrentLimits.BandwidthPriority.Normal) },
        startAddedTorrents = remember { mutableStateOf(true) },
        enabledLabels = remember { mutableStateListOf("42") },
        allLabels = remember { mutableStateOf(emptyList()) },
        shouldShowLabels = remember { mutableStateOf(true) },

        filesTree = remember { TorrentFilesTree(coroutineScope, dispatcher = Dispatchers.Main) },

        addTorrent = {},
        addTorrentState = remember { mutableStateOf(null) },
        onMergeTrackersDialogResult = {}
    )
}
