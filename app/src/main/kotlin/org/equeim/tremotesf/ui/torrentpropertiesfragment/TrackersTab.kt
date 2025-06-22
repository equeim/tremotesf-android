// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Parcelable
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.requests.torrentproperties.Tracker
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfErrorPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfFloatingActionButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfMultiSelectionPanel
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.components.rememberTremotesfMultiSelectionState
import org.equeim.tremotesf.ui.components.selectableBackground
import org.equeim.tremotesf.ui.components.tremotesfMultiSelectionClickable
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentViewModel.TrackerItem
import java.time.Duration
import java.time.Instant

@Composable
fun TrackersTab(
    innerPadding: PaddingValues,
    trackers: RpcRequestState<List<TrackerItem>>,
    toolbarClicked: Flow<Unit>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    torrentOperations: TorrentOperations
) {
    TremotesfScreenContentWithPlaceholder(
        requestState = trackers,
        onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
        modifier = Modifier.fillMaxSize(),
        placeholdersModifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(Dimens.screenContentPadding())
    ) { trackers ->
        val listState = rememberLazyListState()
        LaunchedEffect(toolbarClicked) {
            toolbarClicked.collect { listState.scrollToItem(0) }
        }

        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + Dimens.PaddingForSelectionPanel
        )

        val selectionState = rememberTremotesfMultiSelectionState(
            listItems = trackers,
            keySelector = { it.tracker.id }
        )

        val comparator: Comparator<TrackerItem> =
            remember(LocalConfiguration.current.locales) { compareBy(AlphanumericComparator()) { it.tracker.announceUrl } }
        val sortedTrackers = remember { derivedStateOf { trackers.sortedWith(comparator) } }

        var showAddTrackersDialog: Boolean by rememberSaveable { mutableStateOf(false) }
        var showEditTrackerDialog: EditTrackerDialogParams? by rememberSaveable { mutableStateOf(null) }
        var showRemoveTrackersDialog: RemoveTrackersDialogParams? by rememberSaveable { mutableStateOf(null) }

        Box {
            LazyColumn(
                state = listState,
                contentPadding = listPadding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = sortedTrackers.value,
                    key = { it.tracker.id }
                ) { item ->
                    Column {
                        Box {
                            ListItem(
                                headlineContent = { Text(item.tracker.announceUrl) },
                                supportingContent = {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .weight(3.0f)
                                        ) {
                                            Text(
                                                stringResource(
                                                    when (item.tracker.status) {
                                                        Tracker.Status.Inactive -> R.string.tracker_inactive
                                                        Tracker.Status.WaitingForUpdate -> R.string.tracker_waiting_for_update
                                                        Tracker.Status.QueuedForUpdate -> R.string.tracker_queued_for_update
                                                        Tracker.Status.Updating -> R.string.tracker_updating
                                                    }
                                                )
                                            )
                                            if (item.nextUpdateEta != null) {
                                                Text(
                                                    stringResource(
                                                        R.string.next_update,
                                                        DateUtils.formatElapsedTime(item.nextUpdateEta.seconds)
                                                    )
                                                )
                                            }
                                            item.tracker.errorMessage?.takeIf { it.isNotEmpty() }?.let { error ->
                                                Text(
                                                    text = stringResource(R.string.tracker_error, error),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1.0f)
                                        ) {
                                            Text(
                                                pluralStringResource(
                                                    R.plurals.peers_plural,
                                                    item.tracker.peers,
                                                    item.tracker.peers
                                                )
                                            )
                                            Text(
                                                pluralStringResource(
                                                    R.plurals.seeders_plural,
                                                    item.tracker.seeders,
                                                    item.tracker.seeders
                                                )
                                            )
                                            Text(
                                                pluralStringResource(
                                                    R.plurals.leechers_plural,
                                                    item.tracker.leechers,
                                                    item.tracker.leechers
                                                )
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = selectableBackground(selectionState.isSelected(item.tracker.id))
                                ),
                                modifier = Modifier.tremotesfMultiSelectionClickable(selectionState, item.tracker.id) {
                                    showEditTrackerDialog =
                                        EditTrackerDialogParams(item.tracker.id, item.tracker.announceUrl)
                                }
                            )
                            if (selectionState.isSelected(item.tracker.id)) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(R.string.selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.SpacingBig)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            if (trackers.isEmpty()) {
                TremotesfErrorPlaceholder(
                    error = stringResource(R.string.no_trackers),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(Dimens.screenContentPadding())
                )
            }

            TremotesfMultiSelectionPanel(
                state = selectionState,
                selectedItemsString = R.plurals.trackers_selected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(innerPadding),
                actions = {
                    TremotesfIconButtonWithTooltip(Icons.Filled.Delete, R.string.remove) {
                        showRemoveTrackersDialog = RemoveTrackersDialogParams(selectionState.selectedKeys.toList())
                    }
                }
            )

            if (!selectionState.hasSelection) {
                TremotesfFloatingActionButtonWithTooltip(
                    icon = Icons.Filled.Add,
                    textId = R.string.add_trackers,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(innerPadding)
                        .padding(Dimens.screenContentPadding())
                ) {
                    showAddTrackersDialog = true
                }
            }

            if (showAddTrackersDialog) {
                AddTrackersDialog(torrentOperations) { showAddTrackersDialog = false }
            }

            showEditTrackerDialog?.let { params ->
                EditTrackerDialog(
                    params = params,
                    torrentOperations = torrentOperations
                ) { showEditTrackerDialog = null }
            }

            showRemoveTrackersDialog?.let { params ->
                RemoveTrackersDialog(params, torrentOperations) { showRemoveTrackersDialog = null }
            }
        }
    }
}

@Composable
private fun AddTrackersDialog(
    torrentOperations: TorrentOperations,
    onDismissRequest: () -> Unit
) {
    var announceUrls: String by rememberSaveable { mutableStateOf("") }
    val addTrackers = {
        if (announceUrls.isNotBlank()) {
            val lines = announceUrls.lineSequence().filter(String::isNotEmpty).toList()
            torrentOperations.addTrackers(lines)
            onDismissRequest()
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_trackers)) },
        text = {
            val focusRequester = rememberTremotesfInitialFocusRequester()
            OutlinedTextField(
                value = announceUrls,
                onValueChange = { announceUrls = it },
                label = { Text(stringResource(R.string.trackers_announce_urls)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.height(128.dp).focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = addTrackers,
                enabled = announceUrls.isNotBlank()
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Parcelize
data class EditTrackerDialogParams(
    val id: Int,
    val announceUrl: String
) : Parcelable

@Composable
private fun EditTrackerDialog(
    params: EditTrackerDialogParams,
    torrentOperations: TorrentOperations,
    onDismissRequest: () -> Unit
) {
    var newAnnounceUrl: String by rememberSaveable { mutableStateOf(params.announceUrl) }
    val replaceTracker = {
        if (newAnnounceUrl.isNotBlank()) {
            torrentOperations.replaceTracker(params.id, newAnnounceUrl)
        }
        onDismissRequest()
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.edit_tracker)) },
        text = {
            val focusRequester = rememberTremotesfInitialFocusRequester()
            OutlinedTextField(
                value = newAnnounceUrl,
                onValueChange = { newAnnounceUrl = it },
                singleLine = true,
                label = { Text(stringResource(R.string.tracker_announce_url)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions { replaceTracker() },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = replaceTracker,
                enabled = newAnnounceUrl.isNotBlank()
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Parcelize
private data class RemoveTrackersDialogParams(val ids: List<Int>) : Parcelable

@Composable
private fun RemoveTrackersDialog(
    params: RemoveTrackersDialogParams,
    torrentOperations: TorrentOperations,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Text(
                pluralStringResource(
                    R.plurals.remove_trackers_message,
                    params.ids.size,
                    params.ids.size
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    torrentOperations.removeTrackers(params.ids)
                    onDismissRequest()
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Preview
@Composable
private fun TrackersTabPreview() = ComponentPreview {
    TrackersTab(
        innerPadding = PaddingValues(),
        trackers = remember {
            RpcRequestState.Loaded(
                listOf(
                    TrackerItem(
                        tracker = Tracker(
                            id = 0,
                            announceUrl = "http://example.com",
                            status = Tracker.Status.WaitingForUpdate,
                            lastAnnounceSucceeded = false,
                            lastAnnounceTime = Instant.EPOCH,
                            lastAnnounceResult = "lol",
                            peers = 44,
                            seeders = 11,
                            leechers = 33,
                            nextUpdateTime = Instant.EPOCH,
                            tier = 0
                        ),
                        nextUpdateEta = Duration.ofSeconds(666)
                    )
                )
            )
        },
        toolbarClicked = remember { emptyFlow() },
        navigateToDetailedErrorDialog = {},
        torrentOperations = TORRENT_OPERATIONS_FOR_PREVIEW
    )
}

@Preview
@Composable
fun AddTrackersDialogPreview() = ScreenPreview {
    AddTrackersDialog(
        torrentOperations = TORRENT_OPERATIONS_FOR_PREVIEW,
        onDismissRequest = {}
    )
}

@Preview
@Composable
fun EditTrackerDialogPreview() = ScreenPreview {
    EditTrackerDialog(
        params = remember { EditTrackerDialogParams(id = 0, announceUrl = "http://example.com") },
        onDismissRequest = {},
        torrentOperations = TORRENT_OPERATIONS_FOR_PREVIEW
    )
}
