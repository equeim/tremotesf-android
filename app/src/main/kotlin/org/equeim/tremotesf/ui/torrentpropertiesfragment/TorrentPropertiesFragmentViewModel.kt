// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.hasSubscribersDebounced
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performPeriodicRequest
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.reannounceTorrents
import org.equeim.tremotesf.rpc.requests.removeTorrents
import org.equeim.tremotesf.rpc.requests.startTorrents
import org.equeim.tremotesf.rpc.requests.startTorrentsNow
import org.equeim.tremotesf.rpc.requests.stopTorrents
import org.equeim.tremotesf.rpc.requests.torrentproperties.Peer
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentDetails
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.Tracker
import org.equeim.tremotesf.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentDetails
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentFiles
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentPeers
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentTrackers
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentWebSeeders
import org.equeim.tremotesf.rpc.requests.torrentproperties.removeTorrentTrackers
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import org.equeim.tremotesf.rpc.requests.torrentproperties.replaceTorrentTracker
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentBandwidthPriority
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentDownloadSpeedLimited
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentHonorSessionLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentIdleSeedingLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentIdleSeedingLimitMode
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentPeersLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentRatioLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentRatioLimitMode
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentUploadSpeedLimited
import org.equeim.tremotesf.rpc.requests.verifyTorrents
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TorrentPropertiesFragmentViewModel(
    val torrentHashString: String,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val detailsRefreshRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val torrentDetails: StateFlow<RpcRequestState<TorrentDetails>> =
        GlobalRpcClient.performPeriodicRequest(detailsRefreshRequests) {
            getTorrentDetails(torrentHashString)
        }.stateIn(GlobalRpcClient, viewModelScope)

    val shouldShowLabels: StateFlow<Boolean> = GlobalRpcClient.serverCapabilitiesFlow.map {
        it?.supportsLabels == true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val quickReturnEnabled: StateFlow<Boolean> =
        Settings.quickReturn.flow().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var shouldNavigateUp: Boolean by mutableStateOf(false)
        private set

    val torrentOperations: TorrentOperations = object : TorrentOperations {
        val details: TorrentDetails? get() = (torrentDetails.value as? RpcRequestState.Loaded)?.response

        override fun start() {
            performRequestAndRefresh(
                R.string.torrents_start_error,
                detailsRefreshRequests
            ) { startTorrents(setOf(it.id)) }
        }

        override fun startNow() {
            performRequestAndRefresh(
                R.string.torrents_start_error,
                detailsRefreshRequests
            ) { startTorrentsNow(setOf(it.id)) }
        }

        override fun pause() {
            performRequestAndRefresh(
                R.string.torrents_pause_error,
                detailsRefreshRequests
            ) { stopTorrents(setOf(it.id)) }
        }

        override fun check() {
            performRequestAndRefresh(
                R.string.torrents_check_error,
                detailsRefreshRequests
            ) { verifyTorrents(setOf(it.id)) }
        }

        override fun reannounce() {
            performRequest(R.string.torrents_reannounce_error) { reannounceTorrents(setOf(it.id)) }
        }

        override fun remove(deleteFiles: Boolean) {
            performRequest(R.string.torrents_remove_error) { removeTorrents(listOf(it.hashString), deleteFiles) }
            shouldNavigateUp = true
        }

        override fun rename(newName: String) {
            if (_filesTreeState.value is FilesTreeState.Loaded) {
                filesTree.renameFile(TorrentFilesTree.NodePath(intArrayOf(0)), newName)
            } else {
                performRequestAndRefresh(R.string.file_rename_error, detailsRefreshRequests) {
                    renameTorrentFile(it.hashString, it.name, newName)
                }
            }
        }

        override fun addTrackers(announceUrls: List<String>) {
            val trackers = getExistingTrackers() ?: return
            performRequestAndRefresh(R.string.trackers_add_error, trackersRefreshRequests) {
                addTorrentTrackers(
                    torrentHashString = it.hashString,
                    trackersToAdd = announceUrls.map { url -> setOf(url) },
                    existingTrackersMaybe = trackers
                )
            }
        }

        override fun replaceTracker(trackerId: Int, newAnnounceUrl: String) {
            val trackers = getExistingTrackers() ?: return
            performRequestAndRefresh(R.string.tracker_replace_error, trackersRefreshRequests) {
                replaceTorrentTracker(
                    torrentHashString = it.hashString,
                    trackerId = trackerId,
                    newAnnounceUrl = newAnnounceUrl,
                    allTrackers = trackers
                )
            }
        }

        override fun removeTrackers(trackerIds: List<Int>) {
            val trackers = getExistingTrackers() ?: return
            performRequestAndRefresh(R.string.trackers_remove_error, trackersRefreshRequests) {
                removeTorrentTrackers(
                    torrentHashString = it.hashString,
                    trackerIds = trackerIds,
                    allTrackers = trackers
                )
            }
        }

        private fun getExistingTrackers(): List<Tracker>? =
            (trackers.value as? RpcRequestState.Loaded)?.response?.map { it.tracker }

        private fun performRequest(
            @StringRes errorContext: Int,
            block: suspend RpcClient.(details: TorrentDetails) -> Unit
        ) {
            val details = this.details
            if (details == null) {
                Timber.e("performRequest: torrent details are not loaded")
                return
            }
            GlobalRpcClient.performBackgroundRpcRequest(errorContext) { block(details) }
        }

        private fun performRequestAndRefresh(
            @StringRes errorContext: Int,
            refreshRequests: MutableSharedFlow<Unit>,
            block: suspend RpcClient.(details: TorrentDetails) -> Unit
        ) {
            val details = this.details
            if (details == null) {
                Timber.e("performRequestAndRefresh: torrent details are not loaded")
                return
            }
            viewModelScope.launch {
                val ok = GlobalRpcClient.awaitBackgroundRpcRequest(errorContext) { block(details) }
                if (ok) refreshRequests.emit(Unit)
            }
        }
    }

    val filesTree = RpcTorrentFilesTree(
        torrentHashString = torrentHashString,
        parentScope = viewModelScope,
        onTorrentRenamed = { detailsRefreshRequests.tryEmit(Unit) }
    )

    sealed interface FilesTreeState {
        data object Loading : FilesTreeState
        data class Loaded(val torrentHasFiles: Boolean) : FilesTreeState
        data class Error(val error: RpcRequestError) : FilesTreeState
    }

    private val _filesTreeState = MutableStateFlow<FilesTreeState>(FilesTreeState.Loading)
    val filesTreeState: StateFlow<FilesTreeState> by ::_filesTreeState

    data class TrackerItem(
        val tracker: Tracker,
        val nextUpdateEta: java.time.Duration?
    )

    private val trackersRefreshRequests = MutableSharedFlow<Unit>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackers: StateFlow<RpcRequestState<List<TrackerItem>>> = GlobalRpcClient.performPeriodicRequest(
        manualRefreshRequests = trackersRefreshRequests,
    ) { getTorrentTrackers(torrentHashString) }.transformLatest {
        when (it) {
            is RpcRequestState.Loaded -> processTrackers(it.response)
            is RpcRequestState.Error -> emit(it)
            is RpcRequestState.Loading -> emit(it)
        }
    }.stateIn(GlobalRpcClient, viewModelScope)

    val peers: StateFlow<RpcRequestState<List<Peer>>> = GlobalRpcClient.performPeriodicRequest {
        getTorrentPeers(torrentHashString)
    }.stateIn(GlobalRpcClient, viewModelScope)

    val webSeeders: StateFlow<RpcRequestState<List<String>>> = GlobalRpcClient.performPeriodicRequest {
        getTorrentWebSeeders(torrentHashString)
    }.stateIn(GlobalRpcClient, viewModelScope)

    val limits: StateFlow<RpcRequestState<TorrentLimits>> = GlobalRpcClient.performRecoveringRequest {
        getTorrentLimits(torrentHashString)
    }.stateIn(GlobalRpcClient, viewModelScope)

    val torrentLimitsOperations: TorrentLimitsOperations = object : TorrentLimitsOperations {
        override fun setHonorSessionLimits(honor: Boolean) {
            performRequest { setTorrentHonorSessionLimits(torrentHashString, honor) }
        }

        override fun setDownloadSpeedLimited(limited: Boolean) {
            performRequest { setTorrentDownloadSpeedLimited(torrentHashString, limited) }
        }

        override fun setDownloadSpeedLimit(limit: TransferRate) {
            performRequest { setTorrentDownloadSpeedLimit(torrentHashString, limit) }
        }

        override fun setUploadSpeedLimited(limited: Boolean) {
            performRequest { setTorrentUploadSpeedLimited(torrentHashString, limited) }
        }

        override fun setUploadSpeedLimit(limit: TransferRate) {
            performRequest { setTorrentUploadSpeedLimit(torrentHashString, limit) }
        }

        override fun setBandwidthPriority(priority: TorrentLimits.BandwidthPriority) {
            performRequest { setTorrentBandwidthPriority(torrentHashString, priority) }
        }

        override fun setRatioLimitMode(mode: TorrentLimits.RatioLimitMode) {
            performRequest { setTorrentRatioLimitMode(torrentHashString, mode) }
        }

        override fun setRatioLimit(limit: Double) {
            performRequest { setTorrentRatioLimit(torrentHashString, limit) }
        }

        override fun setIdleSeedingLimitMode(mode: TorrentLimits.IdleSeedingLimitMode) {
            performRequest { setTorrentIdleSeedingLimitMode(torrentHashString, mode) }
        }

        override fun setIdleSeedingLimit(limit: Duration) {
            performRequest { setTorrentIdleSeedingLimit(torrentHashString, limit) }
        }

        override fun setPeersLimit(limit: Long) {
            performRequest { setTorrentPeersLimit(torrentHashString, limit) }
        }

        private fun performRequest(
            block: suspend RpcClient.() -> Unit
        ) {
            GlobalRpcClient.performBackgroundRpcRequest(R.string.set_torrent_limits_error) { block() }
        }
    }

    init {
        viewModelScope.launch {
            _filesTreeState.hasSubscribersDebounced().collectLatest { hasSubscribers ->
                if (!hasSubscribers) return@collectLatest
                GlobalRpcClient.performPeriodicRequest {
                    getTorrentFiles(torrentHashString)
                }.collect { requestState ->
                    when (requestState) {
                        is RpcRequestState.Loading -> {
                            if (_filesTreeState.value is FilesTreeState.Error) {
                                _filesTreeState.value = FilesTreeState.Loading
                            }
                        }

                        is RpcRequestState.Loaded -> {
                            val files = requestState.response
                            when (val state = _filesTreeState.value) {
                                is FilesTreeState.Loading, is FilesTreeState.Error -> {
                                    if (!files.files.isEmpty()) {
                                        filesTree.createTree(
                                            rpcFiles = files,
                                            savedStateHandle = savedStateHandle
                                        )
                                        _filesTreeState.value = FilesTreeState.Loaded(torrentHasFiles = true)
                                    } else {
                                        _filesTreeState.value = FilesTreeState.Loaded(torrentHasFiles = false)
                                    }
                                }

                                is FilesTreeState.Loaded -> {
                                    if (!state.torrentHasFiles && !files.files.isEmpty()) {
                                        _filesTreeState.value = FilesTreeState.Loading
                                        filesTree.createTree(files, savedStateHandle)
                                    } else {
                                        filesTree.updateTree(files)
                                    }
                                }
                            }
                        }

                        is RpcRequestState.Error -> {
                            _filesTreeState.value = FilesTreeState.Error(requestState.error)
                            filesTree.reset()
                        }
                    }
                }
            }
        }
    }

    private suspend fun FlowCollector<RpcRequestState<List<TrackerItem>>>.processTrackers(trackers: List<Tracker>) {
        var items = trackers.map { TrackerItem(it, it.calculateEta()) }
        emit(RpcRequestState.Loaded(items))
        while (currentCoroutineContext().isActive) {
            delay(1.seconds)
            items = items.map {
                if (it.nextUpdateEta == null) it else it.copy(nextUpdateEta = it.tracker.calculateEta())
            }
            emit(RpcRequestState.Loaded(items))
        }
    }

    private companion object {
        fun Tracker.calculateEta(): java.time.Duration? =
            nextUpdateTime?.let { java.time.Duration.between(Instant.now(), it) }
    }
}

interface TorrentOperations {
    fun start()
    fun startNow()
    fun pause()
    fun check()
    fun reannounce()
    fun remove(deleteFiles: Boolean)
    fun rename(newName: String)
    fun addTrackers(announceUrls: List<String>)
    fun replaceTracker(trackerId: Int, newAnnounceUrl: String)
    fun removeTrackers(trackerIds: List<Int>)
}

val TORRENT_OPERATIONS_FOR_PREVIEW: TorrentOperations by lazy {
    object : TorrentOperations {
        override fun start() = Unit
        override fun startNow() = Unit
        override fun pause() = Unit
        override fun check() = Unit
        override fun reannounce() = Unit
        override fun remove(deleteFiles: Boolean) = Unit
        override fun rename(newName: String) = Unit
        override fun addTrackers(announceUrls: List<String>) = Unit
        override fun replaceTracker(trackerId: Int, newAnnounceUrl: String) = Unit
        override fun removeTrackers(trackerIds: List<Int>) = Unit
    }
}

interface TorrentLimitsOperations {
    fun setHonorSessionLimits(honor: Boolean)
    fun setDownloadSpeedLimited(limited: Boolean)
    fun setDownloadSpeedLimit(limit: TransferRate)
    fun setUploadSpeedLimited(limited: Boolean)
    fun setUploadSpeedLimit(limit: TransferRate)
    fun setBandwidthPriority(priority: TorrentLimits.BandwidthPriority)
    fun setRatioLimitMode(mode: TorrentLimits.RatioLimitMode)
    fun setRatioLimit(limit: Double)
    fun setIdleSeedingLimitMode(mode: TorrentLimits.IdleSeedingLimitMode)
    fun setIdleSeedingLimit(limit: Duration)
    fun setPeersLimit(limit: Long)
}

val TORRENT_LIMITS_OPERATIONS_FOR_PREVIEW: TorrentLimitsOperations by lazy {
    object : TorrentLimitsOperations {
        override fun setHonorSessionLimits(honor: Boolean) = Unit
        override fun setDownloadSpeedLimited(limited: Boolean) = Unit
        override fun setDownloadSpeedLimit(limit: TransferRate) = Unit
        override fun setUploadSpeedLimited(limited: Boolean) = Unit
        override fun setUploadSpeedLimit(limit: TransferRate) = Unit
        override fun setBandwidthPriority(priority: TorrentLimits.BandwidthPriority) = Unit
        override fun setRatioLimitMode(mode: TorrentLimits.RatioLimitMode) = Unit
        override fun setRatioLimit(limit: Double) = Unit
        override fun setIdleSeedingLimitMode(mode: TorrentLimits.IdleSeedingLimitMode) = Unit
        override fun setIdleSeedingLimit(limit: Duration) = Unit
        override fun setPeersLimit(limit: Long) = Unit
    }
}
