// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Restore
import org.equeim.tremotesf.ui.SortAscending
import org.equeim.tremotesf.ui.SortDescending
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.Companion.statusFilterAcceptsTorrent
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.SortAndFilterSettings
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.SortMode
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.SortOrder
import org.equeim.tremotesf.ui.torrentslistfragment.TorrentsListFragmentViewModel.StatusFilterMode
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                val sortOrder = sortAndFilterSettings.sortOrder.collectAsStateWithLifecycle()
                SortOrderButton(SortOrder.Ascending, sortOrder, sortAndFilterSettings::setSortOrder)
                SortOrderButton(SortOrder.Descending, sortOrder, sortAndFilterSettings::setSortOrder)
            }
        }

        val comparator = remember(LocalConfiguration.current.locales) { AlphanumericComparator() }
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
            TremotesfComboBox(
                currentItem = sortAndFilterSettings.labelFilter.collectAsStateWithLifecycle()::value,
                updateCurrentItem = sortAndFilterSettings::setLabelFilter,
                items = calculatedFilters.sortedLabels,
                itemDisplayString = {
                    val count = calculatedFilters.labelsCounts.getOrDefault(it, 0)
                    if (it.isEmpty()) {
                        stringResource(R.string.torrents_all, count)
                    } else {
                        stringResource(R.string.directories_spinner_text, it, count)
                    }
                },
                label = R.string.labels,
                modifier = Modifier.fillMaxWidth()
            )
        }

        TremotesfComboBox(
            currentItem = sortAndFilterSettings.trackerFilter.collectAsStateWithLifecycle()::value,
            updateCurrentItem = sortAndFilterSettings::setTrackerFilter,
            items = calculatedFilters.sortedTrackers,
            itemDisplayString = {
                val count = calculatedFilters.trackersCounts.getOrDefault(it, 0)
                if (it.isEmpty()) {
                    stringResource(R.string.torrents_all, count)
                } else {
                    stringResource(R.string.trackers_spinner_text, it, count)
                }
            },
            label = R.string.trackers,
            modifier = Modifier.fillMaxWidth()
        )

        TremotesfComboBox(
            currentItem = sortAndFilterSettings.directoryFilter.collectAsStateWithLifecycle()::value,
            updateCurrentItem = sortAndFilterSettings::setDirectoryFilter,
            items = calculatedFilters.sortedDirectories,
            itemDisplayString = {
                val count = calculatedFilters.directoriesCounts.getOrDefault(it, 0)
                if (it.isEmpty()) {
                    stringResource(R.string.torrents_all, count)
                } else {
                    stringResource(R.string.directories_spinner_text, it, count)
                }
            },
            label = R.string.directories,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortOrderButton(
    sortOrder: SortOrder,
    currentSortOrder: State<SortOrder>,
    setSortOrder: (SortOrder) -> Unit
) {
    ToggleButton(
        checked = currentSortOrder.value == sortOrder,
        onCheckedChange = {
            if (it) {
                setSortOrder(sortOrder)
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val text = stringResource(
                when (sortOrder) {
                    SortOrder.Ascending -> R.string.ascending
                    SortOrder.Descending -> R.string.descending
                }
            )
            Icon(
                when (sortOrder) {
                    SortOrder.Ascending -> Icons.AutoMirrored.Filled.SortAscending
                    SortOrder.Descending -> Icons.AutoMirrored.Filled.SortDescending
                },
                text
            )
            Text(text)
        }
    }
}

private data class CalculatedFilters(
    val statusFilterModesCounts: Map<StatusFilterMode, Int>,
    val sortedLabels: List<String>,
    val labelsCounts: Map<String, Int>,
    val sortedTrackers: List<String>,
    val trackersCounts: Map<String, Int>,
    val sortedDirectories: List<String>,
    val directoriesCounts: Map<String, Int>
)

private fun calculateFilters(
    torrents: List<Torrent>,
    labelsEnabled: Boolean,
    comparator: AlphanumericComparator
): CalculatedFilters {
    val modes = mutableMapOf(StatusFilterMode.All to torrents.size)
    val labels = if (labelsEnabled) {
        mutableMapOf("" to torrents.size)
    } else {
        null
    }
    val trackers = mutableMapOf("" to torrents.size)
    val directories = mutableMapOf("" to torrents.size)
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
        directories.compute(torrent.downloadDirectory.toNativeSeparators(), IncrementCount)
    }
    return CalculatedFilters(
        statusFilterModesCounts = modes,
        sortedLabels = labels?.keys?.sortedWith(comparator) ?: emptyList(),
        labelsCounts = labels ?: emptyMap(),
        sortedTrackers = trackers.keys.sortedWith(comparator),
        trackersCounts = trackers,
        sortedDirectories = directories.keys.sortedWith(comparator),
        directoriesCounts = directories
    )
}

private object IncrementCount : BiFunction<Any, Int?, Int> {
    override fun apply(key: Any, count: Int?): Int {
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
                directoryFilter = MutableStateFlow(""),
                isAnySettingChanged = MutableStateFlow(true)
            )
        },
        labelsEnabled = remember { mutableStateOf(true) },
        allTorrents = remember { mutableStateOf(emptyList()) },
    )
}
