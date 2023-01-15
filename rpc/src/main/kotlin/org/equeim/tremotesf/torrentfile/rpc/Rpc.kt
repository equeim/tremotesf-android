/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

@file:Suppress("ObjectPropertyName")

package org.equeim.tremotesf.torrentfile.rpc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import org.equeim.libtremotesf.*
import org.equeim.tremotesf.common.DefaultTremotesfDispatchers
import org.equeim.tremotesf.common.MutableEventFlow
import org.equeim.tremotesf.common.TremotesfDispatchers
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

data class ServerStats(
    var downloadSpeed: Long,
    var uploadSpeed: Long,
    var currentSession: SessionStats,
    var total: SessionStats
) {
    constructor() : this(0, 0, SessionStats(), SessionStats())
}

private class NativeCallbackException(methodName: String, cause: Throwable) : RuntimeException(
    "Exception in $methodName: ${cause.message}",
    cause
)

abstract class Rpc(protected val servers: Servers, protected val scope: CoroutineScope, context: Context, protected val dispatchers: TremotesfDispatchers = DefaultTremotesfDispatchers) {
    init {
        LibTremotesf.init(javaClass.classLoader)
    }

    val nativeInstance: JniRpc = object : JniRpc() {
        private inline fun runFromNativeCallback(
            block: () -> Unit
        ) {
            runCatching {
                block()
            }.getOrElse {
                val exception = NativeCallbackException(
                    Throwable().stackTrace.first().methodName,
                    it
                )
                Thread { throw exception }.start()
            }
        }

        override fun onConnectionStateChanged(connectionState: RpcConnectionState) =
            runFromNativeCallback {
                Timber.i("onConnectionStateChanged() called with: connectionState = $connectionState")
                _connectionState.value = connectionState
            }

        override fun onErrorChanged(error: RpcError, errorMessage: String, detailedErrorMessage: String) =
            runFromNativeCallback {
                Timber.i("onErrorChanged() called with: error = $error, errorMessage = $errorMessage")
                _error.value = Error(error, errorMessage, detailedErrorMessage)
            }

        override fun onServerSettingsChanged(data: JniServerSettingsData) =
            runFromNativeCallback {
                val old = serverSettings
                serverSettings = data
                old.delete()
            }

        override fun onTorrentsUpdated(
            removedIndexRanges: IntPairVector,
            changed: TorrentDataVector,
            added: TorrentDataVector
        ) = runFromNativeCallback {
            // We need call Rpc.onTorrentsUpdated() in blocking fashion to prevent state
            // inconsistency when other callbacks are called
            // Run in Unconfined to reduce thread jumping when using PublicSuffixList
            runBlocking(dispatchers.Unconfined) {
                this@Rpc.onTorrentsUpdated(
                    removedIndexRanges,
                    changed.map(libtremotesf::moveFromVector),
                    added.map(libtremotesf::moveFromVector))
            }
        }

        override fun onServerStatsUpdated(
            downloadSpeed: Long,
            uploadSpeed: Long,
            currentSession: SessionStats,
            total: SessionStats
        ) = runFromNativeCallback {
            _serverStats.value = ServerStats(downloadSpeed, uploadSpeed, currentSession, total)
        }

        override fun onTorrentAdded(id: Int, hashString: String, name: String) =
            runFromNativeCallback {
                Timber.i("onTorrentAdded() called with: id = $id, hashString = $hashString, name = $name")
                this@Rpc.onTorrentAdded(id, hashString, name)
            }

        override fun onTorrentFinished(id: Int, hashString: String, name: String) =
            runFromNativeCallback {
                Timber.i("onTorrentFinished() called with: id = $id, hashString = $hashString, name = $name")
                this@Rpc.onTorrentFinished(id, hashString, name)
            }

        override fun onTorrentAddDuplicate() = runFromNativeCallback {
            Timber.i("onTorrentAddDuplicate() called")
            _torrentAddDuplicateEvents.tryEmit(Unit)
        }

        override fun onTorrentAddError() = runFromNativeCallback {
            Timber.i("onTorrentAddError() called")
            _torrentAddErrorEvents.tryEmit(Unit)
        }

        override fun onTorrentFilesUpdated(torrentId: Int, files: TorrentFilesVector) =
            runFromNativeCallback {
                onTorrentFilesUpdated(torrentId, files.map(libtremotesf::moveFromVector))
            }

        override fun onTorrentPeersUpdated(
            torrentId: Int,
            removedIndexRanges: IntPairVector,
            changed: TorrentPeersVector,
            added: TorrentPeersVector
        ) = runFromNativeCallback {
            onTorrentPeersUpdated(
                torrentId,
                removedIndexRanges.map { it.first.rangeTo(it.second) },
                changed.map(libtremotesf::moveFromVector),
                added.map(libtremotesf::moveFromVector)
            )
        }

        override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) =
            runFromNativeCallback {
                Timber.i("onTorrentFileRenamed() called with: torrentId = $torrentId, filePath = $filePath, newName = $newName")
                _torrentFileRenamedEvents.tryEmit(
                    TorrentFileRenamedData(
                        torrentId,
                        filePath,
                        newName
                    )
                )
            }

        override fun onGotDownloadDirFreeSpace(bytes: Long) =
            runFromNativeCallback {
                Timber.i("onGotDownloadDirFreeSpace() called with: bytes = $bytes")
                _gotDownloadDirFreeSpaceEvent.tryEmit(bytes)
            }

        override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) =
            runFromNativeCallback {
                Timber.i("onGotFreeSpaceForPath() called with: path = $path, success = $success, bytes = $bytes")
                _gotFreeSpaceForPathEvents.tryEmit(GotFreeSpaceForPathData(path, success, bytes))
            }

        override fun onAboutToDisconnect() = runFromNativeCallback {
            this@Rpc.onAboutToDisconnect()
        }
    }

    init {
        Timber.i("init: initializing native instance")
        nativeInstance.init()
        Timber.i("init: initialized native instance")
    }

    @Volatile
    var serverSettings = JniServerSettingsData()
        private set

    private val _serverStats = MutableStateFlow(ServerStats())
    val serverStats: StateFlow<ServerStats> by ::_serverStats

    private val _connectionState = MutableStateFlow(RpcConnectionState.Disconnected)
    val connectionState: StateFlow<RpcConnectionState> by ::_connectionState

    val isConnected: StateFlow<Boolean> = connectionState
        .map { it == RpcConnectionState.Connected }
        .distinctUntilChanged()
        .stateIn(scope + dispatchers.Unconfined, SharingStarted.Eagerly, false)

    data class Error(val error: RpcError, val errorMessage: String, val detailedErrorMessage: String)

    private val _error = MutableStateFlow(Error(RpcError.NoError, "", ""))
    val error: StateFlow<Error> by ::_error

    data class Status(
        val connectionState: RpcConnectionState = RpcConnectionState.Disconnected,
        val error: Error = Error(RpcError.NoError, "", "")
    ) {
        val isConnected: Boolean
            get() = connectionState == RpcConnectionState.Connected
    }

    val status = combine(connectionState, error, Rpc::Status)
        .stateIn(scope + dispatchers.Unconfined, SharingStarted.Eagerly, Status())

    private val _torrentAddDuplicateEvents = MutableEventFlow<Unit>()
    val torrentAddDuplicateEvents: Flow<Unit> by ::_torrentAddDuplicateEvents

    private val _torrentAddErrorEvents = MutableEventFlow<Unit>()
    val torrentAddErrorEvents: Flow<Unit> by ::_torrentAddErrorEvents

    data class TorrentFilesUpdatedData(val torrentId: Int, val changedFiles: List<TorrentFile>)

    private val _torrentFilesUpdatedEvents = MutableEventFlow<TorrentFilesUpdatedData>()
    val torrentFilesUpdatedEvents: Flow<TorrentFilesUpdatedData> by ::_torrentFilesUpdatedEvents

    data class TorrentPeersUpdatedData(
        val torrentId: Int,
        val removedIndexRanges: List<IntRange>,
        val changed: List<Peer>,
        val added: List<Peer>
    )

    private val _torrentPeersUpdatedEvents = MutableEventFlow<TorrentPeersUpdatedData>()
    val torrentPeersUpdatedEvents: Flow<TorrentPeersUpdatedData> by ::_torrentPeersUpdatedEvents

    data class TorrentFileRenamedData(val torrentId: Int, val filePath: String, val newName: String)

    private val _torrentFileRenamedEvents = MutableEventFlow<TorrentFileRenamedData>()
    val torrentFileRenamedEvents: Flow<TorrentFileRenamedData> by ::_torrentFileRenamedEvents

    private val _gotDownloadDirFreeSpaceEvent = MutableEventFlow<Long>()
    val gotDownloadDirFreeSpaceEvents: Flow<Long> by ::_gotDownloadDirFreeSpaceEvent

    data class GotFreeSpaceForPathData(val path: String, val success: Boolean, val bytes: Long)

    private val _gotFreeSpaceForPathEvents = MutableEventFlow<GotFreeSpaceForPathData>()
    val gotFreeSpaceForPathEvents: Flow<GotFreeSpaceForPathData> by ::_gotFreeSpaceForPathEvents

    private val _torrentsUpdatedEvent = MutableEventFlow<Unit>()
    val torrentsUpdatedEvent: Flow<Unit> by ::_torrentsUpdatedEvent

    private val _torrents = MutableStateFlow<List<Torrent>>(emptyList())
    val torrents: StateFlow<List<Torrent>> by ::_torrents

    private val publicSuffixList = PublicSuffixList(context)

    protected val disconnectingAfterCurrentServerChanged = AtomicBoolean(false)

    private var connectedOnce = false

    val wifiNetworkController = WifiNetworkServersController(servers, scope, context)

    init {
        servers.lastTorrentsProvider = Servers.LastTorrentsProvider {
            if (isConnected.value) {
                Server.LastTorrents(true, torrents.value.map {
                    Server.Torrent(
                        it.id,
                        it.hashString,
                        it.name,
                        it.isFinished
                    )
                })
            } else {
                null
            }
        }

        servers.serversState.value.currentServer?.let {
            setConnectionConfiguration(it.toConnectionConfiguration())
        }

        servers.currentServer
            .map { it?.toConnectionConfiguration() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { configuration ->
                if (isConnected.value) {
                    disconnectingAfterCurrentServerChanged.set(true)
                }
                if (configuration != null) {
                    setConnectionConfiguration(configuration)
                    nativeInstance.connect()
                } else {
                    Timber.i("Calling resetConnectionConfiguration()")
                    nativeInstance.resetConnectionConfiguration()
                }
            }
            .launchIn(scope)

        Timber.i("init: finished initialization")
    }

    @AnyThread
    private fun setConnectionConfiguration(configuration: ConnectionConfiguration) {
        Timber.i("Calling setConnectionConfiguration() with: address = ${configuration.address}, port = ${configuration.port}")
        nativeInstance.setConnectionConfiguration(configuration)
    }

    @MainThread
    fun connectOnce() {
        Timber.i("connectOnce() called")
        if (!connectedOnce) {
            Timber.i("connectOnce: first connection")
            wifiNetworkController.setCurrentServerFromWifiNetwork()
            nativeInstance.connect()
            connectedOnce = true
        } else {
            Timber.i("connectOnce: already connected once")
        }
    }

    @MainThread
    fun disconnectOnShutdown() {
        Timber.i("disconnectOnShutdown() called")
        nativeInstance.disconnect()
        connectedOnce = false
    }

    @WorkerThread
    private suspend fun onTorrentsUpdated(
        removedIndexRanges: List<IntPair>,
        changed: List<TorrentData>,
        added: List<TorrentData>
    ) {
        val newTorrents = torrents.value.toMutableList()

        for (range in removedIndexRanges) {
            newTorrents.subList(range.first, range.second).clear()
        }

        val trackerSitesCache = mutableMapOf<String, String?>()

        if (changed.isNotEmpty()) {
            val changedIter = changed.iterator()
            var changedTorrentData = changedIter.next()
            var changedId = changedTorrentData.id
            val torrentsIter = newTorrents.listIterator()
            while (torrentsIter.hasNext()) {
                val torrent = torrentsIter.next()
                if (torrent.id == changedId) {
                    torrentsIter.set(Torrent.create(changedTorrentData, this, torrent, publicSuffixList, trackerSitesCache))
                    if (changedIter.hasNext()) {
                        changedTorrentData = changedIter.next()
                        changedId = changedTorrentData.id
                    } else {
                        changedId = -1
                    }
                }
            }
        }

        for (torrentData in added) {
            newTorrents.add(Torrent.create(torrentData, this, null, publicSuffixList, trackerSitesCache))
        }

        _torrents.value = newTorrents
        _torrentsUpdatedEvent.tryEmit(Unit)
    }

    protected open fun onTorrentFinished(id: Int, hashString: String, name: String) = Unit
    protected open fun onTorrentAdded(id: Int, hashString: String, name: String) = Unit

    @AnyThread
    private fun onTorrentFilesUpdated(torrentId: Int, files: List<TorrentFile>) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.filesEnabled) {
                _torrentFilesUpdatedEvents.tryEmit(TorrentFilesUpdatedData(torrentId, files))
            }
        }
    }

    @AnyThread
    private fun onTorrentPeersUpdated(
        torrentId: Int,
        removed: List<IntRange>,
        changed: List<Peer>,
        added: List<Peer>
    ) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.peersEnabled) {
                _torrentPeersUpdatedEvents.tryEmit(
                    TorrentPeersUpdatedData(
                        torrentId,
                        removed,
                        changed,
                        added
                    )
                )
            }
        }
    }

    @AnyThread
    private fun onAboutToDisconnect() {
        Timber.i("onAboutToDisconnect() called")
        if (!disconnectingAfterCurrentServerChanged.get()) {
            Timber.i("onAboutToDisconnect: saving servers")
            servers.saveCurrentServerLastTorrents()
        } else {
            Timber.i("onAboutToDisconnect: current server changed, do nothing")
        }
    }
}
