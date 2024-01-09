// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.PeriodicServerStateUpdater
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performPeriodicRequest
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.getTorrentsList
import org.equeim.tremotesf.rpc.requests.removeTorrents
import org.equeim.tremotesf.rpc.requests.startTorrents
import org.equeim.tremotesf.rpc.requests.startTorrentsNow
import org.equeim.tremotesf.rpc.requests.stopTorrents
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import org.equeim.tremotesf.rpc.requests.verifyTorrents
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.SavedStateFlowHolder
import org.equeim.tremotesf.ui.utils.savedStateFlow
import org.threeten.bp.Instant
import timber.log.Timber
import kotlin.time.Duration

class TorrentsListFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    companion object {
        fun statusFilterAcceptsTorrent(torrent: Torrent, filterMode: StatusFilterMode): Boolean {
            return when (filterMode) {
                StatusFilterMode.Active -> (torrent.status == TorrentStatus.Downloading && !torrent.isDownloadingStalled) ||
                        (torrent.status == TorrentStatus.Seeding && !torrent.isSeedingStalled)

                StatusFilterMode.Downloading -> when (torrent.status) {
                    TorrentStatus.Downloading,
                    TorrentStatus.QueuedForDownloading,
                    -> true

                    else -> false
                }

                StatusFilterMode.Seeding -> when (torrent.status) {
                    TorrentStatus.Seeding,
                    TorrentStatus.QueuedForSeeding,
                    -> true

                    else -> false
                }

                StatusFilterMode.Paused -> (torrent.status == TorrentStatus.Paused)
                StatusFilterMode.Checking -> (torrent.status == TorrentStatus.Checking)
                StatusFilterMode.Errored -> (torrent.error != null)
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

    private fun <T : Any> Settings.Property<T>.setAsync(value: T) {
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
        sortMode != SortMode.DEFAULT ||
                sortOrder != SortOrder.DEFAULT ||
                statusFilterMode != StatusFilterMode.DEFAULT ||
                (trackerFilter as String).isNotEmpty() ||
                (directoryFilter as String).isNotEmpty()
    }

    private val refreshRequests = MutableSharedFlow<Unit>()
    private val _torrentsLoadedEvents = MutableSharedFlow<Unit>()
    val torrentsLoadedEvents: Flow<Unit> by ::_torrentsLoadedEvents

    val allTorrents: StateFlow<RpcRequestState<List<Torrent>>> =
        GlobalRpcClient.performPeriodicRequest(refreshRequests) { getTorrentsList() }
            .onEach {
                when (it) {
                    is RpcRequestState.Loaded, is RpcRequestState.Error -> _torrentsLoadedEvents.emit(Unit)
                    else -> Unit
                }
            }
            .stateIn(GlobalRpcClient, viewModelScope)

    val torrentsListState: StateFlow<RpcRequestState<List<Torrent>>> =
        allTorrents.filterAndSortTorrents().stateIn(GlobalRpcClient, viewModelScope)

    val sortSettingsChanged: Flow<Unit> = combine(sortMode, sortOrder, ::Pair)
        .drop(1)
        .transform { emit(Unit) }

    data class SubtitleState(val downloadSpeed: TransferRate, val uploadSpeed: TransferRate)

    val subtitleState: StateFlow<SubtitleState?> =
        PeriodicServerStateUpdater.sessionStats
            .map {
                (it as? RpcRequestState.Loaded)?.let { stats ->
                    SubtitleState(
                        stats.response.downloadSpeed,
                        stats.response.uploadSpeed
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val searchViewIsIconified: SavedStateFlowHolder<Boolean> by savedStateFlow(
        savedStateHandle,
        true
    )
    private val torrentsLoaded: Flow<Boolean> =
        torrentsListState.map { it is RpcRequestState.Loaded }.distinctUntilChanged()
    val showSearchView: Flow<Boolean> = torrentsLoaded
    val showTransmissionSettingsButton: Flow<Boolean> = combine(
        GlobalServers.hasServers,
        searchViewIsIconified.flow(),
        Boolean::and
    ).distinctUntilChanged()
    val showTorrentsFiltersButton: Flow<Boolean> = combine(
        torrentsLoaded,
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
        torrentsListState,
        searchViewIsIconified.flow()
    ) { torrentsListState, searchViewIsIconified ->
        when {
            !searchViewIsIconified -> ConnectionButtonState.Hidden
            else -> when (torrentsListState) {
                is RpcRequestState.Error -> {
                    when (torrentsListState.error) {
                        is RpcRequestError.NoConnectionConfiguration -> ConnectionButtonState.AddServer
                        is RpcRequestError.ConnectionDisabled -> ConnectionButtonState.Connect
                        else -> ConnectionButtonState.Disconnect
                    }
                }

                is RpcRequestState.Loading -> ConnectionButtonState.Disconnect
                is RpcRequestState.Loaded -> ConnectionButtonState.Hidden
            }
        }
    }.distinctUntilChanged()

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
        notificationPermissionHelper?.permissionRequestResult?.receiveAsFlow()?.filter { !it }?.onEach {
            Timber.d("Notification permission denied")
            onNotificationPermissionRequestDismissed()
        }?.launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            refreshRequests.emit(Unit)
            PeriodicServerStateUpdater.sessionStateRefreshRequests.emit(Unit)
        }
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

    fun startTorrents(torrentIds: List<Int>, now: Boolean) {
        Timber.d("startTorrents() called with: torrentIds = $torrentIds, now = $now")
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_start_error) {
                if (now) {
                    startTorrents(torrentIds)
                } else {
                    startTorrentsNow(torrentIds)
                }
            }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    fun pauseTorrents(torrentIds: List<Int>) {
        Timber.d("pauseTorrents() called with: torrentIds = $torrentIds")
        viewModelScope.launch {
            val ok =
                GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_start_error) { stopTorrents(torrentIds) }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    fun checkTorrents(torrentIds: List<Int>) {
        Timber.d("checkTorrents() called with: torrentIds = $torrentIds")
        viewModelScope.launch {
            val ok =
                GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_start_error) { verifyTorrents(torrentIds) }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    fun renameTorrentFile(torrentHashString: String, filePath: String, newName: String) {
        Timber.d(
            "renameTorrentFile() called with: torrentHashString = $torrentHashString, filePath = $filePath, newName = $newName"
        )
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.file_rename_error) {
                renameTorrentFile(torrentHashString, filePath, newName)
            }
            if (ok && !torrentHashString.contains('/')) {
                refreshRequests.emit(Unit)
            }
        }
    }

    fun removeTorrents(torrentHashStrings: List<String>, deleteFiles: Boolean) {
        Timber.d("removeTorrents() called with: torrentHashStrings = $torrentHashStrings, deleteFiles = $deleteFiles")
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_remove_error) {
                removeTorrents(torrentHashStrings, deleteFiles)
            }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    private fun Flow<RpcRequestState<List<Torrent>>>.filterAndSortTorrents(): Flow<RpcRequestState<List<Torrent>>> {
        val filterPredicateFlow = combine(
            statusFilterMode,
            trackerFilter,
            directoryFilter,
            nameFilter.flow(),
            ::createFilterPredicate
        )
        val comparatorFlow = combine(sortMode, sortOrder, ::createComparator)
        return combine(this, filterPredicateFlow, comparatorFlow) { requestState, filterPredicate, comparator ->
            if (requestState is RpcRequestState.Loaded) {
                RpcRequestState.Loaded(
                    requestState.response.asSequence().filter(filterPredicate).sortedWith(comparator).toList()
                )
            } else {
                requestState
            }
        }
    }

    private fun createFilterPredicate(
        statusFilterMode: StatusFilterMode,
        trackerFilter: String,
        directoryFilter: String,
        nameFilter: String,
    ): (Torrent) -> Boolean {
        return { torrent: Torrent ->
            statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
                    (trackerFilter.isEmpty() || (torrent.trackerSites.find { it == trackerFilter } != null)) &&
                    (directoryFilter.isEmpty() || torrent.downloadDirectory.value == directoryFilter) &&
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
                    SortMode.Eta -> nullsFirst<Duration>().compare(o1.eta, o2.eta)
                    SortMode.Ratio -> o1.ratio.compareTo(o2.ratio)
                    SortMode.Size -> o1.totalSize.bytes.compareTo(o2.totalSize.bytes)
                    SortMode.AddedDate -> nullsFirst<Instant>().compare(o1.addedDate, o2.addedDate)
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
