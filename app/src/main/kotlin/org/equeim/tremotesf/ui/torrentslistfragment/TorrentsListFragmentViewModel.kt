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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.getStateFlow
import org.equeim.tremotesf.utils.Logger

import java.util.concurrent.TimeUnit

class TorrentsListFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application), Logger {
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

    val sortMode = savedStateHandle.getStateFlow("sortMode", Settings.torrentsSortMode)
    val sortOrder = savedStateHandle.getStateFlow("sortOrder", Settings.torrentsSortOrder)

    val statusFilterMode = savedStateHandle.getStateFlow("statusFilter", Settings.torrentsStatusFilter)
    val trackerFilter = savedStateHandle.getStateFlow("trackerFilter", Settings.torrentsTrackerFilter)
    val directoryFilter = savedStateHandle.getStateFlow("directoryFilter", Settings.torrentsDirectoryFilter)
    val nameFilter = savedStateHandle.getStateFlow("nameFilter", "")

    private val filteredTorrents = combine(Rpc.torrents, statusFilterMode, trackerFilter, directoryFilter, nameFilter) { torrents, status, tracker, directory, name ->
        torrents.filter(createFilterPredicate(status, tracker, directory, name))
    }
    val torrents = combine(filteredTorrents, sortMode, sortOrder) { torrents, sortMode, sortOrder ->
        torrents.sortedWith(createComparator(sortMode, sortOrder))
        // Stop filtering/sorting when there is no subscribers, but add timeout to account for configuration changes
    }.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(500), emptyList())

    val subtitleUpdateData = Rpc.serverStats.combine(Rpc.isConnected, ::Pair)

    val showAddTorrentDuplicateError = MutableStateFlow(false)
    val showAddTorrentError = MutableStateFlow(false)

    val navigatedFromFragment = savedStateHandle.getStateFlow("navigatedFromFragment", false)

    init {
        Rpc.torrentAddDuplicateEvents
                .onEach { showAddTorrentDuplicateError.value = true }
                .launchIn(viewModelScope)
        Rpc.torrentAddErrorEvents
                .onEach { showAddTorrentError.value = true }
                .launchIn(viewModelScope)
    }

    fun shouldShowDonateDialog(): Boolean {
        if (Settings.donateDialogShown) {
            return false
        }
        val info = getApplication<Application>().packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
        val currentTime = System.currentTimeMillis()
        val installDays = TimeUnit.DAYS.convert(currentTime - info.firstInstallTime, TimeUnit.MILLISECONDS)
        val updateDays = TimeUnit.DAYS.convert(currentTime - info.lastUpdateTime, TimeUnit.MILLISECONDS)
        return (installDays >= 2 && updateDays >= 1)
    }

    private fun createFilterPredicate(statusFilterMode: StatusFilterMode,
                                      trackerFilter: String,
                                      directoryFilter: String,
                                      nameFilter: String): (Torrent) -> Boolean {
        return { torrent: Torrent ->
            statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
                    (trackerFilter.isEmpty() || (torrent.trackerSites.find { it == trackerFilter } != null)) &&
                    (directoryFilter.isEmpty() || torrent.downloadDirectory == directoryFilter) &&
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

    private fun <T> SavedStateHandle.getStateFlow(key: String, initialValue: T) = getStateFlow(viewModelScope, key, initialValue)
}
