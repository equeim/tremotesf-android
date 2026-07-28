// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Restore
import org.equeim.tremotesf.ui.SortAscending
import org.equeim.tremotesf.ui.SortDescending
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.torrentslist.TorrentsListScreenViewModel.Companion.statusFilterAcceptsTorrent
import org.equeim.tremotesf.ui.torrentslist.TorrentsListScreenViewModel.SortAndFilterSettings
import org.equeim.tremotesf.ui.torrentslist.TorrentsListScreenViewModel.SortMode
import org.equeim.tremotesf.ui.torrentslist.TorrentsListScreenViewModel.SortOrder
import org.equeim.tremotesf.ui.torrentslist.TorrentsListScreenViewModel.StatusFilterMode
import org.equeim.tremotesf.ui.utils.rememberAlphanumericComparator
import java.util.function.BiFunction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersBottomSheet(
    onDismissRequest: () -> Unit,
    sortAndFilterSettings: SortAndFilterSettings,
    labelsEnabled: State<Boolean>,
    allTorrents: State<List<Torrent>>
) {
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismissRequest
    ) {
        FiltersBottomSheetContent(
            sortAndFilterSettings = sortAndFilterSettings,
            labelsEnabled = labelsEnabled,
            allTorrents = allTorrents
        )
    }
}

@Composable
private fun FiltersBottomSheetContent(
    sortAndFilterSettings: SortAndFilterSettings,
    labelsEnabled: State<Boolean>,
    allTorrents: State<List<Torrent>>
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenContentPaddingHorizontal())
            .padding(bottom = Dimens.screenContentPaddingVertical()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(LocalMinimumInteractiveComponentSize.current)
        ) {
            val showResetButton = sortAndFilterSettings.isAnySettingChanged.collectAsStateWithLifecycle()
            @Suppress("RemoveRedundantQualifierName", "RedundantSuppression")
            androidx.compose.animation.AnimatedVisibility(visible = showResetButton.value) {
                TremotesfIconButtonWithTooltip(
                    icon = Icons.Filled.Restore,
                    textId = R.string.reset,
                    modifier = Modifier.align(Alignment.Start + Alignment.CenterVertically),
                    onClick = { sortAndFilterSettings.reset() }
                )
            }
            Text(
                text = stringResource(R.string.torrents_filters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        TremotesfComboBox(
            currentItem = sortAndFilterSettings.sortMode.collectAsStateWithLifecycle()::value,
            updateCurrentItem = sortAndFilterSettings::setSortMode,
            items = SortMode.entries,
            itemDisplayString = {
                stringResource(
                    when (it) {
                        SortMode.Name -> R.string.name
                        SortMode.Status -> R.string.status
                        SortMode.Progress -> R.string.progress
                        SortMode.Eta -> R.string.eta
                        SortMode.Ratio -> R.string.ratio
                        SortMode.Size -> R.string.size
                        SortMode.AddedDate -> R.string.added_date
                        SortMode.LastActivity -> R.string.last_activity
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            label = R.string.sort
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingBig),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.sort_order))
            SortOrderButtons(sortAndFilterSettings.sortOrder.collectAsStateWithLifecycle(), sortAndFilterSettings::setSortOrder)
        }

        val comparator = rememberAlphanumericComparator()
        val calculatedFilters: CalculatedFilters by remember {
            derivedStateOf {
                calculateFilters(
                    allTorrents.value,
                    labelsEnabled.value,
                    comparator
                )
            }
        }

        TremotesfComboBox(
            currentItem = sortAndFilterSettings.statusFilterMode::value,
            updateCurrentItem = sortAndFilterSettings::setStatusFilterMode,
            items = StatusFilterMode.entries,
            itemDisplayString = {
                stringResource(
                    when (it) {
                        StatusFilterMode.All -> R.string.torrents_all
                        StatusFilterMode.Active -> R.string.torrents_active
                        StatusFilterMode.Downloading -> R.string.torrents_downloading
                        StatusFilterMode.Seeding -> R.string.torrents_seeding
                        StatusFilterMode.Paused -> R.string.torrents_paused
                        StatusFilterMode.Checking -> R.string.torrents_checking
                        StatusFilterMode.Errored -> R.string.torrents_errored
                    },
                    calculatedFilters.statusFilterModesCounts.getOrDefault(it, 0)
                )
            },
            label = R.string.status,
            modifier = Modifier.fillMaxWidth()
        )

        if (labelsEnabled.value) {
            val currentLabelFilterString by sortAndFilterSettings.labelFilter.collectAsStateWithLifecycle()
            val currentLabelFilter = remember {
                derivedStateOf {
                    calculatedFilters.labels.find { it.label == currentLabelFilterString }
                        ?: CalculatedFilters.LabelFilter(currentLabelFilterString, 0)
                }
            }
            TremotesfComboBox(
                currentItem = currentLabelFilter::value,
                updateCurrentItem = { sortAndFilterSettings.setLabelFilter(it.label) },
                items = calculatedFilters.labels,
                itemDisplayString = {
                    if (it.label == "") {
                        stringResource(R.string.torrents_all, it.torrentsCount)
                    } else {
                        stringResource(R.string.directories_spinner_text, it.label, it.torrentsCount)
                    }
                },
                label = R.string.labels,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val currentTrackerFilterString by sortAndFilterSettings.trackerFilter.collectAsStateWithLifecycle()
        val currentTrackerFilter = remember {
            derivedStateOf {
                calculatedFilters.trackers.find { it.trackerSite == currentTrackerFilterString }
                    ?: CalculatedFilters.TrackerFilter(currentTrackerFilterString, 0)
            }
        }
        TremotesfComboBox(
            currentItem = currentTrackerFilter::value,
            updateCurrentItem = { sortAndFilterSettings.setTrackerFilter(it.trackerSite) },
            items = calculatedFilters.trackers,
            itemDisplayString = {
                if (it.trackerSite.isEmpty()) {
                    stringResource(R.string.torrents_all, it.torrentsCount)
                } else {
                    stringResource(R.string.trackers_spinner_text, it.trackerSite, it.torrentsCount)
                }
            },
            label = R.string.trackers,
            modifier = Modifier.fillMaxWidth()
        )

        val currentDirectoryFilter by sortAndFilterSettings.directoryFilter.collectAsStateWithLifecycle()
        val currentDirectoryFilterCalculated = remember {
            derivedStateOf {
                calculatedFilters.directories.find { it.directory == currentDirectoryFilter }
                    ?: CalculatedFilters.DirectoryFilter(
                        directory = currentDirectoryFilter,
                        displayDirectory = currentDirectoryFilter.toNativeSeparators(),
                        torrentsCount = 0
                    )
            }
        }
        TremotesfComboBox(
            currentItem = currentDirectoryFilterCalculated::value,
            updateCurrentItem = { sortAndFilterSettings.setDirectoryFilter(it.directory) },
            items = calculatedFilters.directories,
            itemDisplayString = {
                if (it.directory.isEmpty()) {
                    stringResource(R.string.torrents_all, it.torrentsCount)
                } else {
                    stringResource(R.string.directories_spinner_text, it.displayDirectory, it.torrentsCount)
                }
            },
            label = R.string.directories,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SortOrderButtons(
    currentSortOrder: State<SortOrder>,
    setSortOrder: (SortOrder) -> Unit
) {
    SingleChoiceSegmentedButtonRow {
        for ((index, sortOrder) in SortOrder.entries.withIndex()) {
            val text = stringResource(
                when (sortOrder) {
                    SortOrder.Ascending -> R.string.ascending
                    SortOrder.Descending -> R.string.descending
                }
            )
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SortOrder.entries.size),
                selected = currentSortOrder.value == sortOrder,
                onClick = {
                    if (sortOrder != currentSortOrder.value) {
                        setSortOrder(sortOrder)
                    }
                },
                icon = {
                    Icon(
                        when (sortOrder) {
                            SortOrder.Ascending -> Icons.AutoMirrored.Filled.SortAscending
                            SortOrder.Descending -> Icons.AutoMirrored.Filled.SortDescending
                        },
                        text
                    )
                },
                label = { Text(text) }
            )
        }
    }
}

private data class CalculatedFilters(
    val statusFilterModesCounts: Map<StatusFilterMode, Int>,
    val labels: List<LabelFilter>,
    val trackers: List<TrackerFilter>,
    val directories: List<DirectoryFilter>
) {
    data class LabelFilter(val label: String, val torrentsCount: Int)
    data class TrackerFilter(val trackerSite: String, val torrentsCount: Int)
    data class DirectoryFilter(val directory: NormalizedRpcPath, val displayDirectory: String, val torrentsCount: Int)
}

private fun calculateFilters(
    torrents: List<Torrent>,
    labelsEnabled: Boolean,
    comparator: AlphanumericComparator
): CalculatedFilters {
    val modes = mutableMapOf(StatusFilterMode.All to torrents.size)
    val labels = if (labelsEnabled) {
        sortedMapOf(comparator, "" to torrents.size)
    } else {
        null
    }
    val trackers = sortedMapOf(comparator, "" to torrents.size)
    val directories = sortedMapOf(compareBy(comparator, NormalizedRpcPath::value), NormalizedRpcPath.EMPTY to torrents.size)
    for (torrent in torrents) {
        for (mode in STATUS_FILTER_MODES_WITHOUT_ALL) {
            if (statusFilterAcceptsTorrent(torrent, mode)) {
                modes.compute(mode, IncrementCount)
            }
        }
        if (labels != null) {
            for (label in torrent.labels) {
                labels.compute(label, IncrementCount)
            }
        }
        for (tracker in torrent.trackerSites) {
            trackers.compute(tracker, IncrementCount)
        }
        directories.compute(torrent.downloadDirectory, IncrementCount)
    }
    return CalculatedFilters(
        statusFilterModesCounts = modes,
        labels = labels?.map { CalculatedFilters.LabelFilter(it.key, it.value) }.orEmpty(),
        trackers = trackers.map { CalculatedFilters.TrackerFilter(it.key, it.value) },
        directories = directories.map {
            CalculatedFilters.DirectoryFilter(
                directory = it.key,
                displayDirectory = it.key.toNativeSeparators(),
                torrentsCount = it.value
            )
        }
    )
}

private object IncrementCount : BiFunction<Any?, Int?, Int> {
    override fun apply(key: Any?, count: Int?): Int {
        return (count ?: 0) + 1
    }
}

private val STATUS_FILTER_MODES_WITHOUT_ALL = StatusFilterMode.entries - StatusFilterMode.All

@Preview
@Composable
private fun FiltersBottomSheetPreview() = ComponentPreview {
    FiltersBottomSheetContent(
        sortAndFilterSettings = remember {
            SortAndFilterSettings(
                nameFilter = mutableStateOf(""),
                sortMode = MutableStateFlow(SortMode.Name),
                sortOrder = MutableStateFlow(SortOrder.Ascending),
                statusFilterMode = MutableStateFlow(StatusFilterMode.Downloading),
                labelFilter = MutableStateFlow(""),
                trackerFilter = MutableStateFlow(""),
                directoryFilter = MutableStateFlow("".normalizePath(null)),
                isAnySettingChanged = MutableStateFlow(true)
            )
        },
        labelsEnabled = remember { mutableStateOf(true) },
        allTorrents = remember { mutableStateOf(emptyList()) },
    )
}
