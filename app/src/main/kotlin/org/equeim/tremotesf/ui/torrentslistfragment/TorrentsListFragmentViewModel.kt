// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.SavedStateFlowHolder
import org.equeim.tremotesf.ui.utils.savedStateFlow
import timber.log.Timber

class TorrentsListFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: Torrent, filterMode: StatusFilterMode): Boolean {
            return when (filterMode) {
                StatusFilterMode.Active -> (torrent.status == TorrentData.Status.Downloading && !torrent.isDownloadingStalled) ||
                        (torrent.status == TorrentData.Status.Seeding && !torrent.isSeedingStalled)
                StatusFilterMode.Downloading -> when (torrent.status) {
                    TorrentData.Status.Downloading,
                    TorrentData.Status.QueuedForDownloading -> true
                    else -> false
                }
                StatusFilterMode.Seeding -> when (torrent.status) {
                    TorrentData.Status.Seeding,
                    TorrentData.Status.QueuedForSeeding -> true
                    else -> false
                }
                StatusFilterMode.Paused -> (torrent.status == TorrentData.Status.Paused)
                StatusFilterMode.Checking -> (torrent.status == TorrentData.Status.Checking)
                StatusFilterMode.Errored -> (torrent.hasError)
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
        AddedDate;

        companion object {
            val DEFAULT = Name
        }
    }

    enum class SortOrder {
        Ascending,
        Descending;

        fun inverted(): SortOrder {
            return if (this == Ascending) Descending
            else Ascending
        }

        companion object {
            val DEFAULT = Ascending
        }
    }

    enum class StatusFilterMode {
        All,
        Active,
        Downloading,
        Seeding,
        Paused,
        Checking,
        Errored;

        companion object {
            val DEFAULT = All
        }
    }

    val sortMode: Flow<SortMode> = Settings.torrentsSortMode.flow()
    fun setSortMode(mode: SortMode) = Settings.torrentsSortMode.setAsync(mode)
    val sortOrder: Flow<SortOrder> = Settings.torrentsSortOrder.flow()
    fun setSortOrder(order: SortOrder) = Settings.torrentsSortOrder.setAsync(order)
    val statusFilterMode: Flow<StatusFilterMode> = Settings.torrentsStatusFilter.flow()
    fun setStatusFilterMode(mode: StatusFilterMode) = Settings.torrentsStatusFilter.setAsync(mode)
    val trackerFilter: Flow<String> = Settings.torrentsTrackerFilter.flow()
    fun setTrackerFilter(filter: String) = Settings.torrentsTrackerFilter.setAsync(filter)
    val directoryFilter: Flow<String> = Settings.torrentsDirectoryFilter.flow()
    fun setDirectoryFilter(filter: String) = Settings.torrentsDirectoryFilter.setAsync(filter)

    private fun <T : Any> Settings.MutableProperty<T>.setAsync(value: T) {
        viewModelScope.launch { set(value) }
    }

    val nameFilter: SavedStateFlowHolder<String> by savedStateFlow(savedStateHandle, "")

    fun resetSortAndFilters() {
        setSortMode(SortMode.DEFAULT)
        setSortOrder(SortOrder.DEFAULT)
        setStatusFilterMode(StatusFilterMode.DEFAULT)
        setTrackerFilter("")
        setDirectoryFilter("")
    }

    val sortOrFiltersEnabled = combine(
        sortMode,
        sortOrder,
        statusFilterMode,
        trackerFilter,
        directoryFilter,
    ) { (sortMode, sortOrder, statusFilterMode, trackerFilter, directoryFilter) ->
        @Suppress("cast_never_succeeds")
        sortMode != SortMode.DEFAULT ||
                sortOrder != SortOrder.DEFAULT ||
                statusFilterMode != StatusFilterMode.DEFAULT ||
                (trackerFilter as String).isNotEmpty() ||
                (directoryFilter as String).isNotEmpty()
    }

    private val torrentsIfConnected: Flow<List<Torrent>?> = combine(
        GlobalRpc.torrents,
        GlobalRpc.isConnected
    ) { torrents, isConnected ->
        if (torrents.isEmpty() && !isConnected) null
        else torrents
    }//.flowOn(Dispatchers.Main)
    private val filteredTorrents: Flow<Sequence<Torrent>?> = combine(
        torrentsIfConnected,
        statusFilterMode,
        trackerFilter,
        directoryFilter,
        nameFilter.flow()
    ) { torrents, status, tracker, directory, name ->
        torrents?.asSequence()?.filter(createFilterPredicate(status, tracker, directory, name))
    }
    val torrents: StateFlow<List<Torrent>?> =
        combine(filteredTorrents, sortMode, sortOrder) { torrents, sortMode, sortOrder ->
            torrents?.sortedWith(createComparator(sortMode, sortOrder))?.toList()
            // Stop filtering/sorting when there is no subscribers, but add timeout to account for configuration changes
        }.stateIn(
            viewModelScope + Dispatchers.Default,
            SharingStarted.WhileSubscribed(500),
            null
        )

    val sortSettingsChanged: Flow<Unit> = combine(sortMode, sortOrder, ::Pair)
        .drop(1)
        .transform { emit(Unit) }

    val subtitleUpdateData = GlobalRpc.serverStats.combine(GlobalRpc.isConnected, ::Pair)

    val searchViewIsIconified: SavedStateFlowHolder<Boolean> by savedStateFlow(
        savedStateHandle,
        true
    )
    val showSearchView: Flow<Boolean> = GlobalRpc.isConnected
    val showTransmissionSettingsButton: Flow<Boolean> = combine(
        GlobalServers.hasServers,
        searchViewIsIconified.flow(),
        Boolean::and
    ).distinctUntilChanged()
    val showTorrentsFiltersButton: Flow<Boolean> = combine(
        GlobalRpc.isConnected,
        searchViewIsIconified.flow(),
        Boolean::and
    ).distinctUntilChanged()
    val showAddTorrentButton: Flow<Boolean> = showTorrentsFiltersButton

    enum class ConnectionButtonState {
        AddServer,
        Connect,
        Disconnect,
        Hidden
    }

    val connectionButtonState: Flow<ConnectionButtonState> = combine(
        GlobalServers.hasServers,
        GlobalRpc.connectionState,
        searchViewIsIconified.flow()
    ) { hasServers, connectionState, searchViewIsIconified ->
        when {
            !searchViewIsIconified -> ConnectionButtonState.Hidden
            !hasServers -> ConnectionButtonState.AddServer
            connectionState == RpcConnectionState.Disconnected -> ConnectionButtonState.Connect
            connectionState == RpcConnectionState.Connecting -> ConnectionButtonState.Disconnect
            else -> ConnectionButtonState.Hidden
        }
    }.distinctUntilChanged()

    private val hasTorrents = torrents.map { it?.isNotEmpty() == true }.distinctUntilChanged()

    data class PlaceholderState(val status: Rpc.Status, val hasTorrents: Boolean)

    val placeholderState: Flow<PlaceholderState> =
        combine(GlobalRpc.status, hasTorrents, ::PlaceholderState).distinctUntilChanged()

    val showAddTorrentDuplicateError = MutableStateFlow(false)
    val showAddTorrentError = MutableStateFlow(false)

    val notificationPermissionHelper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimePermissionHelper(
            Manifest.permission.POST_NOTIFICATIONS,
            R.string.notification_permission_rationale,
            showRationaleBeforeRequesting = false
        )
    } else {
        null
    }
    val showNotificationPermissionRequest = MutableStateFlow(false)

    init {
        GlobalRpc.torrentAddDuplicateEvents
            .onEach { showAddTorrentDuplicateError.value = true }
            .launchIn(viewModelScope)
        GlobalRpc.torrentAddErrorEvents
            .onEach { showAddTorrentError.value = true }
            .launchIn(viewModelScope)

        notificationPermissionHelper?.permissionRequestResult?.filter { !it }?.onEach {
            Timber.d("Notification permission denied")
            onNotificationPermissionRequestDismissed()
        }?.launchIn(viewModelScope)
    }

    fun checkNotificationPermission() {
        Timber.d("checkNotificationPermission() called")
        val helper = notificationPermissionHelper ?: return
        viewModelScope.launch {
            if (Settings.userDismissedNotificationPermissionRequest.get()) {
                Timber.d("checkNotificationPermission: user dismissed notification permission request, do nothing")
                return@launch
            }
            val notificationsSettings = listOf(
                Settings.notifyOnAdded,
                Settings.notifyOnFinished,
                Settings.notifyOnAddedSinceLastConnection,
                Settings.notifyOnFinishedSinceLastConnection,
                Settings.showPersistentNotification
            )
            val notificationsSettingsEnabled = combine(
                notificationsSettings.map { property ->
                    property.flow().onEach {
                        Timber.d("${property.key} = $it")
                    }
                }
            ) { properties ->
                properties.any { it }
            }.first()
            if (notificationsSettingsEnabled) {
                if (helper.checkPermission(getApplication())) {
                    Timber.d("checkNotificationPermission: notification permission is granted")
                } else {
                    Timber.d("checkNotificationPermission: notification permission is not granted, show snackbar")
                    showNotificationPermissionRequest.value = true
                }
            } else {
                Timber.d("checkNotificationPermission: notifications settings are disabled, do nothing")
            }
        }
    }

    fun onNotificationPermissionRequestDismissed() {
        Timber.d("onNotificationPermissionRequestDismissed() called")
        viewModelScope.launch {
            Settings.userDismissedNotificationPermissionRequest.set(true)
        }
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
                    SortMode.AddedDate -> o1.addedDate?.compareTo(o2.addedDate) ?: 0
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
