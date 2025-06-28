// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.PeriodicServerStateUpdater
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.performPeriodicRequest
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.getTorrentsList
import org.equeim.tremotesf.rpc.requests.reannounceTorrents
import org.equeim.tremotesf.rpc.requests.removeTorrents
import org.equeim.tremotesf.rpc.requests.serversettings.checkIfAlternativeSpeedLimitsEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsEnabled
import org.equeim.tremotesf.rpc.requests.startTorrents
import org.equeim.tremotesf.rpc.requests.startTorrentsNow
import org.equeim.tremotesf.rpc.requests.stopTorrents
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import org.equeim.tremotesf.rpc.requests.verifyTorrents
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.addtorrent.MergingTrackersMessage
import java.time.Instant
import kotlin.time.Duration

class TorrentsListFragmentViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    data class TitleState(val serverName: String, val serverAddress: String) {
        constructor(server: Server) : this(serverName = server.name, serverAddress = server.address)
    }

    val titleState: StateFlow<TitleState?> = GlobalServers.currentServer.map {
        it?.let(::TitleState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalServers.serversState.value.currentServer?.let(::TitleState))

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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val currentServer: StateFlow<String?> = GlobalServers.currentServer.map { it?.name }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GlobalServers.serversState.value.currentServer?.name
    )

    val servers: StateFlow<List<String>> = GlobalServers.servers.map { it.map(Server::name) }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        GlobalServers.serversState.value.servers.map(Server::name)
    )

    val alternativeSpeedLimitsEnabled: StateFlow<RpcRequestState<Boolean>> = GlobalRpcClient.performRecoveringRequest {
        checkIfAlternativeSpeedLimitsEnabled()
    }.stateIn(GlobalRpcClient, viewModelScope)

    fun setAlternativeSpeedLimitsEnabled(enabled: Boolean) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error) {
            setAlternativeLimitsEnabled(enabled)
        }
    }

    val labelsEnabled: StateFlow<Boolean> = GlobalRpcClient.serverCapabilitiesFlow
        .map { it?.supportsLabels == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    @OptIn(DelicateCoroutinesApi::class)
    class SortAndFilterSettings(
        val nameFilter: MutableState<String>,
        val sortMode: StateFlow<SortMode>,
        val sortOrder: StateFlow<SortOrder>,
        val statusFilterMode: StateFlow<StatusFilterMode>,
        val labelFilter: StateFlow<String>,
        val trackerFilter: StateFlow<String>,
        val directoryFilter: StateFlow<String>,
        val isAnySettingChanged: StateFlow<Boolean>
    ) {
        fun setSortMode(mode: SortMode) {
            GlobalScope.launch { Settings.torrentsSortMode.set(mode) }
        }

        fun setSortOrder(order: SortOrder) {
            GlobalScope.launch { Settings.torrentsSortOrder.set(order) }
        }

        fun setStatusFilterMode(mode: StatusFilterMode) {
            GlobalScope.launch { Settings.torrentsStatusFilter.set(mode) }
        }

        fun setLabelFilter(label: String) {
            GlobalScope.launch { Settings.torrentsLabelFilter.set(label) }
        }

        fun setTrackerFilter(tracker: String) {
            GlobalScope.launch { Settings.torrentsTrackerFilter.set(tracker) }
        }

        fun setDirectoryFilter(directory: String) {
            GlobalScope.launch { Settings.torrentsDirectoryFilter.set(directory) }
        }

        fun reset() {
            nameFilter.value = ""
            setSortMode(SortMode.DEFAULT)
            setSortOrder(SortOrder.DEFAULT)
            setStatusFilterMode(StatusFilterMode.DEFAULT)
            setLabelFilter("")
            setTrackerFilter("")
            setDirectoryFilter("")
        }
    }

    @OptIn(SavedStateHandleSaveableApi::class)
    private val nameFilter by savedStateHandle.saveable<MutableState<String>> { mutableStateOf("") }
    private val nameFilterFlow: Flow<String> get() = snapshotFlow { nameFilter.value }

    val sortAndFilterSettings: StateFlow<SortAndFilterSettings?> = flow {
        emit(
            SortAndFilterSettings(
                nameFilter = nameFilter,
                sortMode = Settings.torrentsSortMode.flow().stateIn(viewModelScope),
                sortOrder = Settings.torrentsSortOrder.flow().stateIn(viewModelScope),
                statusFilterMode = Settings.torrentsStatusFilter.flow().stateIn(viewModelScope),
                labelFilter = Settings.torrentsLabelFilter.flow().stateIn(viewModelScope),
                trackerFilter = Settings.torrentsTrackerFilter.flow().stateIn(viewModelScope),
                directoryFilter = Settings.torrentsDirectoryFilter.flow().stateIn(viewModelScope),
                isAnySettingChanged = combine<Any, Boolean>(
                    nameFilterFlow,
                    Settings.torrentsSortMode.flow(),
                    Settings.torrentsSortOrder.flow(),
                    Settings.torrentsStatusFilter.flow(),
                    Settings.torrentsLabelFilter.flow(),
                    Settings.torrentsTrackerFilter.flow(),
                    Settings.torrentsDirectoryFilter.flow()
                ) { setting ->
                    setting.any {
                        when (it) {
                            is SortMode -> it != SortMode.DEFAULT
                            is SortOrder -> it != SortOrder.DEFAULT
                            is StatusFilterMode -> it != StatusFilterMode.DEFAULT
                            is String -> it.isNotEmpty()
                            // Impossible case
                            else -> false
                        }
                    }
                }.stateIn(viewModelScope)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val refreshRequests = MutableSharedFlow<Unit>()

    val allTorrents: StateFlow<RpcRequestState<List<Torrent>>> =
        GlobalRpcClient.performPeriodicRequest(refreshRequests) { getTorrentsList() }
            .onStart { PeriodicServerStateUpdater.updatingTorrentsOnTorrentsListScreen.value = true }
            .onCompletion { PeriodicServerStateUpdater.updatingTorrentsOnTorrentsListScreen.value = false }
            .onEach {
                if (it is RpcRequestState.Loaded) {
                    PeriodicServerStateUpdater.onTorrentsUpdated(it.response)
                }
                when (it) {
                    is RpcRequestState.Loaded, is RpcRequestState.Error ->
                        // Launch a coroutine to do it in the main thread
                        viewModelScope.launch { _refreshingManually.value = false }

                    else -> Unit
                }
            }
            .stateIn(GlobalRpcClient, viewModelScope, Dispatchers.Default)

    val torrents: StateFlow<RpcRequestState<List<Torrent>>> =
        allTorrents.filterAndSortTorrents().stateIn(GlobalRpcClient, viewModelScope, Dispatchers.Default)

    private val _refreshingManually = mutableStateOf(false)
    val refreshingManually: State<Boolean> by ::_refreshingManually

    fun refreshManually() {
        _refreshingManually.value = true
        viewModelScope.launch {
            refreshRequests.emit(Unit)
            PeriodicServerStateUpdater.sessionStateRefreshRequests.emit(Unit)
        }
    }

    class ListSettings(
        val compactView: StateFlow<Boolean>,
        val multilineName: StateFlow<Boolean>
    )

    val listSettings: StateFlow<ListSettings?> = flow {
        emit(
            ListSettings(
                compactView = Settings.torrentCompactView.flow().stateIn(viewModelScope),
                multilineName = Settings.torrentNameMultiline.flow().stateIn(viewModelScope)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val quickReturnEnabled: StateFlow<Boolean> =
        Settings.quickReturn.flow().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showTransmissionSettingsButton: StateFlow<Boolean> = GlobalServers.hasServers.stateIn(
        viewModelScope,
        SharingStarted.Eagerly, GlobalServers.serversState.value.servers.isNotEmpty()
    )
    val showFiltersAndSearchButtons: StateFlow<Boolean> =
        torrents.map { it is RpcRequestState.Loaded }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    enum class FloatingActionButtonState {
        AddTorrent,
        Connect,
        Disconnect,
        AddServer
    }

    val floatingActionButtonState: StateFlow<FloatingActionButtonState> = torrents.map { torrentsListState ->
        when {
            else -> when (torrentsListState) {
                is RpcRequestState.Error -> {
                    when (torrentsListState.error) {
                        is RpcRequestError.NoConnectionConfiguration -> FloatingActionButtonState.AddServer
                        is RpcRequestError.ConnectionDisabled -> FloatingActionButtonState.Connect
                        else -> FloatingActionButtonState.Disconnect
                    }
                }

                is RpcRequestState.Loading -> FloatingActionButtonState.Disconnect
                is RpcRequestState.Loaded -> FloatingActionButtonState.AddTorrent
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), FloatingActionButtonState.Disconnect)

    private val _checkNotificationPermission = MutableStateFlow<Boolean?>(null)
    val checkNotificationPermission: StateFlow<Boolean?> by ::_checkNotificationPermission

    val showMergingTrackersMessage: MutableState<MergingTrackersMessage?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            _checkNotificationPermission.value = if (Settings.userDismissedNotificationPermissionRequest.get()) {
                false
            } else {
                val notificationsSettings = listOf(
                    Settings.notifyOnAdded,
                    Settings.notifyOnFinished,
                    Settings.notifyOnAddedSinceLastConnection,
                    Settings.notifyOnFinishedSinceLastConnection,
                    Settings.showPersistentNotification
                )
                notificationsSettings.map { async { it.get() } }.awaitAll().any { it }
            }
        }
    }

    fun onCheckedNotificationPermission() {
        _checkNotificationPermission.value = false
    }

    fun onShownNotificationPermissionRequest() {
        viewModelScope.launch {
            Settings.userDismissedNotificationPermissionRequest.set(true)
        }
    }

    val torrentOperations: TorrentsOperations = object : TorrentsOperations {
        override fun start(ids: Set<Int>) =
            performRequestAndRefresh(R.string.torrents_reannounce_error) { startTorrents(ids) }

        override fun startNow(ids: Set<Int>) =
            performRequestAndRefresh(R.string.torrents_reannounce_error) { startTorrentsNow(ids) }

        override fun stop(ids: Set<Int>) =
            performRequestAndRefresh(R.string.torrents_reannounce_error) { stopTorrents(ids) }

        override fun verify(ids: Set<Int>) =
            performRequestAndRefresh(R.string.torrents_reannounce_error) { verifyTorrents(ids) }

        override fun reannounce(ids: Set<Int>) =
            performRequestAndRefresh(R.string.torrents_reannounce_error) { reannounceTorrents(ids) }

        override fun rename(hashString: String, oldName: String, newName: String) =
            performRequestAndRefresh(R.string.file_rename_error) { renameTorrentFile(hashString, oldName, newName) }

        override fun remove(hashStrings: List<String>, deleteFiles: Boolean) =
            performRequestAndRefresh(R.string.torrents_remove_error) { removeTorrents(hashStrings, deleteFiles) }

        private fun performRequestAndRefresh(
            @StringRes errorContext: Int,
            block: suspend RpcClient.() -> Unit
        ) {
            viewModelScope.launch {
                val ok = GlobalRpcClient.awaitBackgroundRpcRequest(errorContext) { block() }
                if (ok) refreshRequests.emit(Unit)
            }
        }
    }

    private fun Flow<RpcRequestState<List<Torrent>>>.filterAndSortTorrents(): Flow<RpcRequestState<List<Torrent>>> {
        val filterPredicateFlow = sortAndFilterSettings.filterNotNull().transform {
            emitAll(
                combine(
                    nameFilterFlow,
                    it.statusFilterMode,
                    it.labelFilter,
                    it.trackerFilter,
                    it.directoryFilter,
                    ::createFilterPredicate
                )
            )
        }
        val comparatorFlow = sortAndFilterSettings.filterNotNull().transform {
            emitAll(combine(it.sortMode, it.sortOrder, ::createComparator))
        }
        return combine(this, filterPredicateFlow, comparatorFlow) { requestState, filterPredicate, comparator ->
            if (requestState is RpcRequestState.Loaded) {
                val torrents = ArrayList<Torrent>(requestState.response.size)
                requestState.response.filterTo(torrents, filterPredicate)
                torrents.sortWith(comparator)
                RpcRequestState.Loaded(torrents)
            } else {
                requestState
            }
        }
    }

    private fun createFilterPredicate(
        nameFilter: String,
        statusFilterMode: StatusFilterMode,
        labelFilter: String?,
        trackerFilter: String,
        directoryFilter: String,
    ): (Torrent) -> Boolean {
        return { torrent: Torrent ->
            (nameFilter.isEmpty() || torrent.name.contains(nameFilter, true)) &&
                    statusFilterAcceptsTorrent(torrent, statusFilterMode) &&
                    (labelFilter.isNullOrEmpty() || torrent.labels.contains(labelFilter)) &&
                    (trackerFilter.isEmpty() || (torrent.trackerSites.contains(trackerFilter))) &&
                    (directoryFilter.isEmpty() || torrent.downloadDirectory.value == directoryFilter)
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
}

interface TorrentsOperations {
    fun start(ids: Set<Int>)
    fun startNow(ids: Set<Int>)
    fun stop(ids: Set<Int>)
    fun verify(ids: Set<Int>)
    fun reannounce(ids: Set<Int>)
    fun rename(hashString: String, oldName: String, newName: String)
    fun remove(hashStrings: List<String>, deleteFiles: Boolean)
}

class FakeTorrentsOperations : TorrentsOperations {
    override fun start(ids: Set<Int>) = Unit
    override fun startNow(ids: Set<Int>) = Unit
    override fun stop(ids: Set<Int>) = Unit
    override fun verify(ids: Set<Int>) = Unit
    override fun reannounce(ids: Set<Int>) = Unit
    override fun rename(hashString: String, oldName: String, newName: String) = Unit
    override fun remove(hashStrings: List<String>, deleteFiles: Boolean) = Unit
}
