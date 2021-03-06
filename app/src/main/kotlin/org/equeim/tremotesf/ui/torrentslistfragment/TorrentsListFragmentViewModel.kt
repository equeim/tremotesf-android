/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Application

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.savedStateFlow
import org.equeim.tremotesf.common.dropTrailingPathSeparator

class TorrentsListFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: Torrent, filterMode: StatusFilterMode): Boolean {
            return when (filterMode) {
                StatusFilterMode.Active -> (torrent.status == TorrentData.Status.Downloading) ||
                        (torrent.status == TorrentData.Status.Seeding)
                StatusFilterMode.Downloading -> when (torrent.status) {
                    TorrentData.Status.Downloading,
                    TorrentData.Status.StalledDownloading,
                    TorrentData.Status.QueuedForDownloading -> true
                    else -> false
                }
                StatusFilterMode.Seeding -> when (torrent.status) {
                    TorrentData.Status.Seeding,
                    TorrentData.Status.StalledSeeding,
                    TorrentData.Status.QueuedForSeeding -> true
                    else -> false
                }
                StatusFilterMode.Paused -> (torrent.status == TorrentData.Status.Paused)
                StatusFilterMode.Checking -> (torrent.status == TorrentData.Status.Checking) ||
                        (torrent.status == TorrentData.Status.Checking)
                StatusFilterMode.Errored -> (torrent.status == TorrentData.Status.Errored)
                StatusFilterMode.All -> true
            }
        }
    }

    enum class SortMode {
        Name,
        Status,
        Progress,
        Eta,
        Ratio,
        Size,
        AddedDate
    }

    enum class SortOrder {
        Ascending,
        Descending;

        fun inverted(): SortOrder {
            return if (this == Ascending) Descending
            else Ascending
        }
    }

    enum class StatusFilterMode {
        All,
        Active,
        Downloading,
        Seeding,
        Paused,
        Checking,
        Errored
    }

    val sortMode by savedStateFlow(savedStateHandle) { Settings.torrentsSortMode }
    val sortOrder by savedStateFlow(savedStateHandle) { Settings.torrentsSortOrder }

    val statusFilterMode by savedStateFlow(savedStateHandle) { Settings.torrentsStatusFilter }
    val trackerFilter by savedStateFlow(savedStateHandle) { Settings.torrentsTrackerFilter }
    val directoryFilter by savedStateFlow(savedStateHandle) { Settings.torrentsDirectoryFilter }
    val nameFilter by savedStateFlow(savedStateHandle) { "" }

    private val filteredTorrents = combine(
        GlobalRpc.torrents,
        statusFilterMode,
        trackerFilter,
        directoryFilter,
        nameFilter
    ) { torrents, status, tracker, directory, name ->
        torrents.asSequence().filter(createFilterPredicate(status, tracker, directory, name))
    }
    val torrents = combine(filteredTorrents, sortMode, sortOrder) { torrents, sortMode, sortOrder ->
        torrents.sortedWith(createComparator(sortMode, sortOrder)).toList()
        // Stop filtering/sorting when there is no subscribers, but add timeout to account for configuration changes
    }.stateIn(
        viewModelScope + Dispatchers.Default,
        SharingStarted.WhileSubscribed(500),
        emptyList()
    )

    val subtitleUpdateData = GlobalRpc.serverStats.combine(GlobalRpc.isConnected, ::Pair)

    private val hasTorrents = torrents.map { it.isNotEmpty() }.distinctUntilChanged()

    data class PlaceholderUpdateData(val status: Rpc.Status, val hasTorrents: Boolean)

    val placeholderUpdateData = combine(GlobalRpc.status, hasTorrents, ::PlaceholderUpdateData)

    val showAddTorrentDuplicateError = MutableStateFlow(false)
    val showAddTorrentError = MutableStateFlow(false)

    init {
        GlobalRpc.torrentAddDuplicateEvents
            .onEach { showAddTorrentDuplicateError.value = true }
            .launchIn(viewModelScope)
        GlobalRpc.torrentAddErrorEvents
            .onEach { showAddTorrentError.value = true }
            .launchIn(viewModelScope)
    }

    private fun createFilterPredicate(
        statusFilterMode: StatusFilterMode,
        trackerFilter: String,
        directoryFilter: String,
        nameFilter: String
    ): (Torrent) -> Boolean {
        return { torrent: Torrent ->
            statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
                    (trackerFilter.isEmpty() || (torrent.trackerSites.find { it == trackerFilter } != null)) &&
                    (directoryFilter.isEmpty() || torrent.downloadDirectory.dropTrailingPathSeparator() == directoryFilter) &&
                    torrent.name.contains(nameFilter, true)
        }
    }

    private fun createComparator(sortMode: SortMode, sortOrder: SortOrder): Comparator<Torrent> {
        return object : Comparator<Torrent> {
            private val nameComparator = AlphanumericComparator()

            override fun compare(o1: Torrent, o2: Torrent): Int {
                var compared = when (sortMode) {
                    SortMode.Name -> nameComparator.compare(o1.name, o2.name)
                    SortMode.Status -> o1.status.compareTo(o2.status)
                    SortMode.Progress -> o1.percentDone.compareTo(o2.percentDone)
                    SortMode.Eta -> o1.eta.compareTo(o2.eta)
                    SortMode.Ratio -> o1.ratio.compareTo(o2.ratio)
                    SortMode.Size -> o1.totalSize.compareTo(o2.totalSize)
                    SortMode.AddedDate -> o1.addedDateTime.compareTo(o2.addedDateTime)
                }
                if (sortMode != SortMode.Name && compared == 0) {
                    compared = nameComparator.compare(o1.name, o2.name)
                }
                if (sortOrder == SortOrder.Descending) {
                    compared = -compared
                }
                return compared
            }
        }
    }
}
