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

@file:Suppress("ObjectPropertyName")

package org.equeim.tremotesf.torrentfile.rpc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.equeim.libtremotesf.IntVector
import org.equeim.libtremotesf.JniRpc
import org.equeim.libtremotesf.JniServerSettingsData
import org.equeim.libtremotesf.LibTremotesf
import org.equeim.libtremotesf.Peer
import org.equeim.libtremotesf.SessionStats
import org.equeim.libtremotesf.TorrentData
import org.equeim.libtremotesf.TorrentDataVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.libtremotesf.TorrentFilesVector
import org.equeim.libtremotesf.TorrentPeersVector
import org.equeim.tremotesf.common.MutableEventFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


typealias RpcConnectionState = org.equeim.libtremotesf.Rpc.Status
typealias RpcError = org.equeim.libtremotesf.Rpc.Error

data class ServerStats(
    var downloadSpeed: Long,
    var uploadSpeed: Long,
    var currentSession: SessionStats,
    var total: SessionStats
) {
    constructor() : this(0, 0, SessionStats(), SessionStats())
}

abstract class Rpc(protected val servers: Servers, protected val scope: CoroutineScope, context: Context) {
    init {
        LibTremotesf.init(javaClass.classLoader)
    }

    val nativeInstance: JniRpc = object : JniRpc() {
        override fun onStatusChanged(status: Int) {
            Timber.i("onStatusChanged() called with: status = $status")
            _connectionState.value = status
        }

        override fun onErrorChanged(error: Int, errorMessage: String) {
            Timber.i("onErrorChanged() called with: error = $error, errorMessage = $errorMessage")
            _error.value = Error(error, errorMessage)
        }

        override fun onServerSettingsChanged(data: JniServerSettingsData) {
            val old = serverSettings
            serverSettings = data
            old.delete()
        }

        override fun onTorrentsUpdated(
            removed: IntVector,
            changed: TorrentDataVector,
            added: TorrentDataVector
        ) {
            this@Rpc.onTorrentsUpdated(removed, changed, added)
            removed.delete()
            changed.delete()
            added.delete()
        }

        override fun onServerStatsUpdated(
            downloadSpeed: Long,
            uploadSpeed: Long,
            currentSession: SessionStats,
            total: SessionStats
        ) {
            _serverStats.value = ServerStats(downloadSpeed, uploadSpeed, currentSession, total)
        }

        override fun onTorrentAdded(id: Int, hashString: String, name: String) {
            Timber.i("onTorrentAdded() called with: id = $id, hashString = $hashString, name = $name")
            this@Rpc.onTorrentAdded(id, hashString, name)
        }

        override fun onTorrentFinished(id: Int, hashString: String, name: String) {
            Timber.i("onTorrentFinished() called with: id = $id, hashString = $hashString, name = $name")
            this@Rpc.onTorrentFinished(id, hashString, name)
        }

        override fun onTorrentAddDuplicate() {
            Timber.i("onTorrentAddDuplicate() called")
            _torrentAddDuplicateEvents.tryEmit(Unit)
        }

        override fun onTorrentAddError() {
            Timber.i("onTorrentAddError() called")
            _torrentAddErrorEvents.tryEmit(Unit)
        }

        override fun onTorrentFilesUpdated(torrentId: Int, files: TorrentFilesVector) {
            onTorrentFilesUpdated(torrentId, files.toList())
            files.delete()
        }

        override fun onTorrentPeersUpdated(
            torrentId: Int,
            removed: IntVector,
            changed: TorrentPeersVector,
            added: TorrentPeersVector
        ) {
            onTorrentPeersUpdated(torrentId, removed.toList(), changed.toList(), added.toList())
            removed.delete()
            changed.delete()
            added.delete()
        }

        override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
            Timber.i("onTorrentFileRenamed() called with: torrentId = $torrentId, filePath = $filePath, newName = $newName")
            _torrentFileRenamedEvents.tryEmit(TorrentFileRenamedData(torrentId, filePath, newName))
        }

        override fun onGotDownloadDirFreeSpace(bytes: Long) {
            Timber.i("onGotDownloadDirFreeSpace() called with: bytes = $bytes")
            _gotDownloadDirFreeSpaceEvent.tryEmit(bytes)
        }

        override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
            Timber.i("onGotFreeSpaceForPath() called with: path = $path, success = $success, bytes = $bytes")
            _gotFreeSpaceForPathEvents.tryEmit(GotFreeSpaceForPathData(path, success, bytes))
        }

        override fun onAboutToDisconnect() = this@Rpc.onAboutToDisconnect()
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
    val connectionState: StateFlow<Int> by ::_connectionState

    val isConnected: StateFlow<Boolean> = connectionState
        .map { it == RpcConnectionState.Connected }
        .distinctUntilChanged()
        .stateIn(scope + Dispatchers.Unconfined, SharingStarted.Eagerly, false)

    data class Error(val error: Int, val errorMessage: String)

    private val _error = MutableStateFlow(Error(RpcError.NoError, ""))
    val error: StateFlow<Error> by ::_error

    data class Status(
        val connectionState: Int = RpcConnectionState.Disconnected,
        val error: Error = Error(RpcError.NoError, "")
    ) {
        val isConnected: Boolean
            get() = connectionState == RpcConnectionState.Connected
    }


    val status = combine(connectionState, error, Rpc::Status)
        .stateIn(scope + Dispatchers.Unconfined, SharingStarted.Eagerly, Status())

    private val _torrentAddDuplicateEvents = MutableEventFlow<Unit>()
    val torrentAddDuplicateEvents: Flow<Unit> by ::_torrentAddDuplicateEvents

    private val _torrentAddErrorEvents = MutableEventFlow<Unit>()
    val torrentAddErrorEvents: Flow<Unit> by ::_torrentAddErrorEvents

    data class TorrentFilesUpdatedData(val torrentId: Int, val changedFiles: List<TorrentFile>)

    private val _torrentFilesUpdatedEvents = MutableEventFlow<TorrentFilesUpdatedData>()
    val torrentFilesUpdatedEvents: Flow<TorrentFilesUpdatedData> by ::_torrentFilesUpdatedEvents

    data class TorrentPeersUpdatedData(
        val torrentId: Int,
        val removed: List<Int>,
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

    private val _torrents = MutableStateFlow<List<Torrent>>(emptyList())
    val torrents: StateFlow<List<Torrent>> by ::_torrents

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

        servers.currentServer.value?.let(::setServer)

        scope.launch {
            servers.currentServer.drop(1).collect { server ->
                Timber.i("Current server changed")

                if (isConnected.value) {
                    disconnectingAfterCurrentServerChanged.set(true)
                }

                if (server != null) {
                    setServer(server)
                    nativeInstance.connect()
                } else {
                    nativeInstance.resetServer()
                }
            }
        }

        Timber.i("init: finished initialization")
    }

    @AnyThread
    private fun setServer(server: Server) {
        Timber.i("setServer() called for server with: name = ${server.name}, address = ${server.address}, port = ${server.port}")

        val s = org.equeim.libtremotesf.Server()
        with(server) {
            s.name = name
            s.address = address
            s.port = port
            s.apiPath = apiPath

            s.proxyType = nativeProxyType()
            s.proxyHostname = proxyHostname
            s.proxyPort = proxyPort
            s.proxyUser = proxyUser
            s.proxyPassword = proxyPassword

            s.https = httpsEnabled
            s.selfSignedCertificateEnabled = selfSignedCertificateEnabled
            s.selfSignedCertificate = selfSignedCertificate.toByteArray()
            s.clientCertificateEnabled = clientCertificateEnabled
            s.clientCertificate = clientCertificate.toByteArray()

            s.authentication = authentication
            s.username = username
            s.password = password

            s.updateInterval = updateInterval
            s.timeout = timeout
        }
        nativeInstance.setServer(s)
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
    protected open fun onTorrentsUpdated(
        removed: List<Int>,
        changed: List<TorrentData>,
        added: List<TorrentData>
    ) {
        val newTorrents = torrents.value.toMutableList()

        for (index in removed) {
            newTorrents.removeAt(index)
        }

        if (changed.isNotEmpty()) {
            val changedIter = changed.iterator()
            var changedTorrentData = changedIter.next()
            var changedId = changedTorrentData.id
            val torrentsIter = newTorrents.listIterator()
            while (torrentsIter.hasNext()) {
                val torrent = torrentsIter.next()
                if (torrent.id == changedId) {
                    torrentsIter.set(Torrent(changedTorrentData, this, torrent))
                    if (changedIter.hasNext()) {
                        changedTorrentData = changedIter.next()
                        changedId = changedTorrentData.id
                    } else {
                        changedId = -1
                    }
                } else {
                    torrent.isChanged = false
                }
            }
        }

        for (torrentData in added) {
            newTorrents.add(Torrent(torrentData, this))
        }

        _torrents.value = newTorrents
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
        removed: List<Int>,
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
            servers.save()
        } else {
            Timber.i("onAboutToDisconnect: current server changed, do nothing")
        }
    }
}
