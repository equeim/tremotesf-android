// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.ArrowDownward
import org.equeim.tremotesf.ui.ArrowUpward
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Label
import org.equeim.tremotesf.ui.Pause
import org.equeim.tremotesf.ui.TorrentRenameDialog
import org.equeim.tremotesf.ui.TorrentsRemoveDialog
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltipAndMenu
import org.equeim.tremotesf.ui.components.TremotesfMultiSelectionPanel
import org.equeim.tremotesf.ui.components.TremotesfMultiSelectionState
import org.equeim.tremotesf.ui.components.TremotesfPlaceholderText
import org.equeim.tremotesf.ui.components.rememberTremotesfMultiSelectionState
import org.equeim.tremotesf.ui.components.selectableBackground
import org.equeim.tremotesf.ui.components.tremotesfMultiSelectionClickable
import org.equeim.tremotesf.ui.utils.FileSizeFormatter
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.formatTorrentEta
import org.equeim.tremotesf.ui.utils.rememberFileSizeFormatter
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes

@Composable
fun TorrentsList(
    innerPadding: PaddingValues,
    torrents: List<Torrent>,
    compactView: Boolean,
    multilineName: Boolean,
    toolbarClicked: Flow<Unit>,
    labelsEnabled: State<Boolean>,
    sortAndFilterSettings: State<TorrentsListFragmentViewModel.SortAndFilterSettings?>,
    torrentsOperations: TorrentsOperations,
    navigateToTorrentPropertiesScreen: (torrentHashString: String) -> Unit,
    navigateToSetLocationDialog: (torrentHashStrings: List<String>, location: String) -> Unit,
    navigateToLabelsEditDialog: (torrentHashStrings: List<String>, enabledLabels: List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(toolbarClicked, listState) {
        toolbarClicked.collect { listState.scrollToItem(0) }
    }
    LaunchedEffect(sortAndFilterSettings, listState) {
        val sortAndFilterSettings = snapshotFlow { sortAndFilterSettings.value }.filterNotNull().first()
        combine(sortAndFilterSettings.sortMode, sortAndFilterSettings.sortOrder, ::Pair)
            .drop(1)
            .collect {
                listState.scrollToItem(0)
            }
    }

    val selectionState = rememberTremotesfMultiSelectionState(torrents, Torrent::id)

    val fileSizeFormatter = rememberFileSizeFormatter()
    val progressFormatter = remember(LocalConfiguration.current.locales) { DecimalFormat("0.#") }

    val layoutDirection = LocalLayoutDirection.current
    val listPadding = PaddingValues(
        start = innerPadding.calculateStartPadding(layoutDirection),
        top = innerPadding.calculateTopPadding(),
        end = innerPadding.calculateEndPadding(layoutDirection),
        // Account for selection panel
        bottom = innerPadding.calculateBottomPadding() + Dimens.PaddingForSelectionPanel
    )

    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listPadding
        ) {
            items(
                items = torrents,
                key = Torrent::id,
                contentType = { if (compactView) ContentType.Compact else ContentType.Card }
            ) { torrent ->
                if (compactView) {
                    TorrentCompactListItem(
                        torrent = torrent,
                        multilineName = multilineName,
                        fileSizeFormatter = fileSizeFormatter,
                        progressFormatter = progressFormatter,
                        selectionState = selectionState,
                        onClick = { navigateToTorrentPropertiesScreen(torrent.hashString) }
                    )
                } else {
                    TorrentCard(
                        torrent = torrent,
                        multilineName = multilineName,
                        fileSizeFormatter = fileSizeFormatter,
                        progressFormatter = progressFormatter,
                        selectionState = selectionState,
                        onClick = { navigateToTorrentPropertiesScreen(torrent.hashString) }
                    )
                }
            }
        }

        if (torrents.isEmpty()) {
            TremotesfPlaceholderText(
                text = stringResource(R.string.no_torrents),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(innerPadding)
                    .padding(Dimens.screenContentPadding())
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            style = defaultMaterialScrollbarStyle(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(innerPadding)
                .fillMaxHeight()
        )

        SelectionPanel(
            selectionState = selectionState,
            innerPadding = innerPadding,
            labelsEnabled = labelsEnabled,
            torrents = rememberUpdatedState(torrents),
            torrentsOperations = torrentsOperations,
            navigateToSetLocationDialog = navigateToSetLocationDialog,
            navigateToLabelsEditDialog = navigateToLabelsEditDialog
        )
    }
}

private enum class ContentType {
    Card,
    Compact
}

@Composable
private fun LazyItemScope.TorrentCard(
    torrent: Torrent,
    multilineName: Boolean,
    fileSizeFormatter: FileSizeFormatter,
    progressFormatter: DecimalFormat,
    selectionState: TremotesfMultiSelectionState<Torrent, Int>,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = selectableBackground(selectionState.isSelected(torrent.id))
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .padding(horizontal = Dimens.screenContentPaddingHorizontal())
            .padding(top = Dimens.SpacingSmall)
            .animateItem()
            .clip(CardDefaults.elevatedShape)
            .tremotesfMultiSelectionClickable(selectionState, torrent.id, onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.SpacingBig)
                .fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionState.isSelected(torrent.id)) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    StatusIcon(torrent.status)
                }
                Text(
                    text = torrent.name,
                    maxLines = if (multilineName) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.0f)
                )
            }
            ProvideTextStyle(
                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = Dimens.SpacingSmall)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (torrent.isFinished) {
                            stringResource(
                                R.string.uploaded_string,
                                fileSizeFormatter.formatFileSize(torrent.sizeWhenDone),
                                fileSizeFormatter.formatFileSize(torrent.totalUploaded)
                            )
                        } else {
                            stringResource(
                                R.string.completed_string,
                                fileSizeFormatter.formatFileSize(torrent.completedSize),
                                fileSizeFormatter.formatFileSize(torrent.sizeWhenDone),
                                progressFormatter.format(torrent.percentDone * 100)
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (torrent.eta?.isPositive() == true) {
                        Text(formatTorrentEta(torrent.eta))
                    }
                }

                LinearProgressIndicator(
                    progress = { torrent.percentDone.toFloat() },
                    modifier = Modifier
                        .padding(top = Dimens.SpacingSmall)
                        .fillMaxWidth(),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )

                Row(
                    modifier = Modifier
                        .padding(top = Dimens.SpacingSmall)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1.0f)) {
                        Text(
                            stringResource(
                                R.string.download_speed_string,
                                fileSizeFormatter.formatTransferRate(torrent.downloadSpeed),
                            )
                        )
                        Text(
                            stringResource(
                                R.string.upload_speed_string,
                                fileSizeFormatter.formatTransferRate(torrent.uploadSpeed)
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1.0f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = torrent.getStatusString(progressFormatter),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                        if (torrent.labels.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall, Alignment.End),
                                modifier = Modifier.padding(top = Dimens.SpacingSmall)
                            ) {
                                for (label in torrent.labels) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.Label,
                                            label,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.TorrentCompactListItem(
    torrent: Torrent,
    multilineName: Boolean,
    fileSizeFormatter: FileSizeFormatter,
    progressFormatter: DecimalFormat,
    selectionState: TremotesfMultiSelectionState<Torrent, Int>,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .animateItem()
                .background(selectableBackground(selectionState.isSelected(torrent.id)))
                .tremotesfMultiSelectionClickable(selectionState, torrent.id, onClick)
                .padding(horizontal = Dimens.screenContentPaddingHorizontal(), vertical = Dimens.SpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionState.isSelected(torrent.id)) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                StatusIcon(torrent.status)
            }
            Text(
                text = torrent.name,
                maxLines = if (multilineName) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.0f)
            )
            ProvideTextStyle(
                MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                if (torrent.downloadSpeed.bytesPerSecond != 0L) {
                    Text(
                        stringResource(
                            R.string.download_speed_string,
                            fileSizeFormatter.formatTransferRate(torrent.downloadSpeed),
                        )
                    )
                }
                if (torrent.uploadSpeed.bytesPerSecond != 0L) {
                    Text(
                        stringResource(
                            R.string.upload_speed_string,
                            fileSizeFormatter.formatTransferRate(torrent.uploadSpeed)
                        )
                    )
                }
                Text(
                    stringResource(
                        if (torrent.downloadSpeed.bytesPerSecond != 0L || torrent.uploadSpeed.bytesPerSecond != 0L) {
                            R.string.progress_string_with_dot
                        } else {
                            R.string.progress_string
                        },
                        progressFormatter.format(torrent.percentDone * 100)
                    )
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun StatusIcon(status: TorrentStatus) {
    Icon(
        when (status) {
            TorrentStatus.Paused -> Icons.Filled.Pause
            TorrentStatus.Downloading,
            TorrentStatus.QueuedForDownloading -> Icons.Filled.ArrowDownward

            TorrentStatus.Seeding,
            TorrentStatus.QueuedForSeeding -> Icons.Filled.ArrowUpward

            TorrentStatus.QueuedForChecking,
            TorrentStatus.Checking -> Icons.Filled.Refresh
        },
        contentDescription = stringResource(R.string.status)
    )
}

@Composable
private fun Torrent.getStatusString(progressFormatter: DecimalFormat): String {
    return when (status) {
        TorrentStatus.Paused -> if (error != null) {
            stringResource(R.string.torrent_paused_with_error, errorString)
        } else {
            stringResource(R.string.torrent_paused)
        }

        TorrentStatus.Downloading -> if (isDownloadingStalled) {
            if (error != null) {
                stringResource(R.string.torrent_downloading_stalled_with_error, errorString)
            } else {
                stringResource(R.string.torrent_downloading_stalled)
            }
        } else {
            val peers = this.peersSendingToUsCount + this.webSeedersSendingToUsCount
            if (error != null) {
                pluralStringResource(
                    R.plurals.torrent_downloading_with_error,
                    peers,
                    peers,
                    errorString
                )
            } else {
                pluralStringResource(
                    R.plurals.torrent_downloading,
                    peers,
                    peers
                )
            }
        }

        TorrentStatus.Seeding -> if (isSeedingStalled) {
            if (error != null) {
                stringResource(R.string.torrent_seeding_stalled_with_error, errorString)
            } else {
                stringResource(R.string.torrent_seeding_stalled)
            }
        } else {
            if (error != null) {
                pluralStringResource(
                    R.plurals.torrent_seeding_with_error,
                    peersGettingFromUsCount,
                    peersGettingFromUsCount,
                    errorString
                )
            } else {
                pluralStringResource(
                    R.plurals.torrent_seeding,
                    peersGettingFromUsCount,
                    peersGettingFromUsCount
                )
            }
        }

        TorrentStatus.QueuedForDownloading,
        TorrentStatus.QueuedForSeeding,
            -> if (error != null) {
            stringResource(R.string.torrent_queued_with_error, errorString)
        } else {
            stringResource(R.string.torrent_queued)
        }

        TorrentStatus.Checking -> if (error != null) {
            stringResource(
                R.string.torrent_checking_with_error,
                progressFormatter.format(recheckProgress * 100),
                errorString
            )
        } else {
            stringResource(
                R.string.torrent_checking,
                progressFormatter.format(recheckProgress * 100)
            )
        }

        TorrentStatus.QueuedForChecking -> if (error != null) {
            stringResource(R.string.torrent_queued_for_checking_with_error, errorString)
        } else {
            stringResource(R.string.torrent_queued_for_checking)
        }
    }
}

@Composable
private fun BoxScope.SelectionPanel(
    selectionState: TremotesfMultiSelectionState<Torrent, Int>,
    innerPadding: PaddingValues,
    labelsEnabled: State<Boolean>,
    torrents: State<List<Torrent>>,
    torrentsOperations: TorrentsOperations,
    navigateToSetLocationDialog: (torrentHashStrings: List<String>, location: String) -> Unit,
    navigateToLabelsEditDialog: (torrentHashStrings: List<String>, enabledLabels: List<String>) -> Unit
) {
    TremotesfMultiSelectionPanel(
        state = selectionState,
        selectedItemsString = R.plurals.torrents_selected,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(innerPadding)
            // Have to handle navigation bar inset manually due to presence of bottomBar in the Scaffold
            // https://issuetracker.google.com/issues/428740551
            .navigationBarsPadding()
    ) {
        val enableStart: Boolean by remember {
            derivedStateOf {
                selectionState.selectedCount > 1 ||
                        selectionState.selectedKeys
                            .firstOrNull()
                            ?.let { id -> torrents.value.find { it.id == id } }
                            ?.status == TorrentStatus.Paused
            }
        }
        val enablePause: Boolean by remember {
            derivedStateOf {
                selectionState.selectedCount > 1 ||
                        selectionState.selectedKeys
                            .firstOrNull()
                            ?.let { id -> torrents.value.find { it.id == id } }
                            ?.status != TorrentStatus.Paused
            }
        }
        TremotesfIconButtonWithTooltip(icon = Icons.Filled.PlayArrow, textId = R.string.start, enabled = enableStart) {
            torrentsOperations.start(selectionState.selectedKeys)
        }
        TremotesfIconButtonWithTooltip(icon = Icons.Filled.Pause, textId = R.string.pause, enabled = enablePause) {
            torrentsOperations.stop(selectionState.selectedKeys)
        }

        val context = LocalContext.current
        TremotesfIconButtonWithTooltip(Icons.Filled.Share, R.string.share) {
            Utils.shareTorrents(
                magnetLinks = torrents.value.mapNotNull { if (selectionState.isSelected(it.id)) it.magnetLink else null },
                context = context
            )
        }

        var showRenameDialogForHashStringAndName: Pair<String, String>? by rememberSaveable { mutableStateOf(null) }
        var showRemoveDialogForHashStrings: List<String>? by rememberSaveable { mutableStateOf(null) }

        TremotesfIconButtonWithTooltipAndMenu(Icons.Filled.MoreVert, R.string.more_options) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.start_now)) },
                enabled = enableStart,
                onClick = {
                    torrentsOperations.startNow(selectionState.selectedKeys)
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.check_local_data)) },
                onClick = {
                    torrentsOperations.verify(selectionState.selectedKeys)
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reannounce)) },
                onClick = {
                    torrentsOperations.reannounce(selectionState.selectedKeys)
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.set_location)) },
                onClick = {
                    getSelectedTorrentsHashStringsAndFirstTorrent(
                        torrents.value,
                        selectionState
                    )?.let { (hashStrings, firstTorrent) ->
                        navigateToSetLocationDialog(hashStrings, firstTorrent.downloadDirectory.toNativeSeparators())
                    }
                    dismiss()
                }
            )
            val enableRename: Boolean by remember { derivedStateOf { selectionState.selectedCount == 1 } }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                enabled = enableRename,
                onClick = {
                    selectionState.selectedKeys.firstOrNull()?.let { id -> torrents.value.find { it.id == id } }?.let {
                        showRenameDialogForHashStringAndName = it.hashString to it.name
                    }
                    dismiss()
                }
            )
            if (labelsEnabled.value) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_labels)) },
                    onClick = {
                        var enabledLabels: List<String>? = null
                        val hashStrings = torrents.value.mapNotNull {
                            if (selectionState.selectedKeys.contains(it.id)) {
                                if (enabledLabels == null) {
                                    enabledLabels = it.labels
                                } else if (enabledLabels.isNotEmpty() && it.labels != enabledLabels) {
                                    enabledLabels = emptyList()
                                }
                                it.hashString
                            } else {
                                null
                            }
                        }
                        if (enabledLabels != null) {
                            navigateToLabelsEditDialog(hashStrings, enabledLabels)
                        }
                        dismiss()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove)) },
                onClick = {
                    getSelectedTorrentsHashStringsAndFirstTorrent(torrents.value, selectionState)?.let { (hashStrings, _) ->
                        showRemoveDialogForHashStrings = hashStrings
                    }
                    dismiss()
                }
            )
        }

        showRenameDialogForHashStringAndName?.let { (hashString, name) ->
            TorrentRenameDialog(
                torrentName = name,
                rename = { torrentsOperations.rename(hashString, name, it) },
                onDismissRequest = { showRenameDialogForHashStringAndName = null }
            )
        }

        showRemoveDialogForHashStrings?.let {
            TorrentsRemoveDialog(
                torrentHashStrings = it,
                removeTorrents = torrentsOperations::remove,
                onDismissRequest = { showRemoveDialogForHashStrings = null }
            )
        }
    }
}

private fun getSelectedTorrentsHashStringsAndFirstTorrent(
    torrents: List<Torrent>,
    selectionState: TremotesfMultiSelectionState<Torrent, Int>
): Pair<List<String>, Torrent>? {
    var firstSelectedTorrent: Torrent? = null
    val hashStrings = torrents.mapNotNull {
        if (selectionState.selectedKeys.contains(it.id)) {
            if (firstSelectedTorrent == null) {
                firstSelectedTorrent = it
            }
            it.hashString
        } else {
            null
        }
    }
    return firstSelectedTorrent?.let { hashStrings to it }
}

@Preview
@Composable
private fun TorrentsListPreview(
    @PreviewParameter(PreviewParametersProvider::class) params: PreviewParameters,
) = ComponentPreview {
    TorrentsList(
        innerPadding = PaddingValues(),
        torrents = remember {
            listOf(
                Torrent(
                    id = 0,
                    hashString = "",
                    magnetLink = "",
                    name = "enwiki-20250601-pages-articles-multistream.xml.bz2",
                    status = TorrentStatus.Downloading,
                    error = null,
                    errorString = "",
                    downloadDirectory = "/".normalizePath(null),
                    percentDone = 0.69,
                    recheckProgress = 0.0,
                    eta = 44.minutes,
                    ratio = 0.5,
                    totalSize = FileSize.fromBytes(25000000000),
                    completedSize = FileSize.fromBytes(17250000000),
                    leftUntilDone = FileSize.fromBytes(7750000000),
                    sizeWhenDone = FileSize.fromBytes(25000000000),
                    totalUploaded = FileSize.fromBytes(8625000000),
                    downloadSpeed = TransferRate.fromKiloBytesPerSecond(55555),
                    uploadSpeed = TransferRate.fromKiloBytesPerSecond(9999),
                    peersSendingToUsCount = 555,
                    peersGettingFromUsCount = 44,
                    webSeedersSendingToUsCount = 3,
                    addedDate = LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                    trackerSites = listOf("http://example.com"),
                    labels = listOf("wiki", "porn?", "lol", "hmm")
                ),
                Torrent(
                    id = 1,
                    hashString = "",
                    magnetLink = "",
                    name = "AAAAAAAAAAAAAAAaenwiki-20250601-pages-articles-multistream.xml.bz2",
                    status = TorrentStatus.Downloading,
                    error = Torrent.Error.LocalError,
                    errorString = "NOPE",
                    downloadDirectory = "/".normalizePath(null),
                    percentDone = 0.69,
                    recheckProgress = 0.0,
                    eta = 44.minutes,
                    ratio = 0.5,
                    totalSize = FileSize.fromBytes(25000000000),
                    completedSize = FileSize.fromBytes(17250000000),
                    leftUntilDone = FileSize.fromBytes(7750000000),
                    sizeWhenDone = FileSize.fromBytes(25000000000),
                    totalUploaded = FileSize.fromBytes(8625000000),
                    downloadSpeed = TransferRate.fromKiloBytesPerSecond(55555),
                    uploadSpeed = TransferRate.fromKiloBytesPerSecond(9999),
                    peersSendingToUsCount = 555,
                    peersGettingFromUsCount = 44,
                    webSeedersSendingToUsCount = 3,
                    addedDate = LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                    trackerSites = listOf("http://example.com")
                )
            )
        },
        compactView = params.compactView,
        multilineName = params.multilineName,
        toolbarClicked = remember { emptyFlow() },
        labelsEnabled = remember { mutableStateOf(true) },
        sortAndFilterSettings = remember { mutableStateOf(null) },
        navigateToTorrentPropertiesScreen = {},
        torrentsOperations = remember { FakeTorrentsOperations() },
        navigateToSetLocationDialog = { _, _ -> },
        navigateToLabelsEditDialog = { _, _ -> },
    )
}

private data class PreviewParameters(
    val compactView: Boolean,
    val multilineName: Boolean
)

private class PreviewParametersProvider : CollectionPreviewParameterProvider<PreviewParameters>(
    buildList {
        for (compactView in listOf(false, true)) {
            for (multilineName in listOf(false, true)) {
                add(PreviewParameters(compactView, multilineName))
            }
        }
    }
)
