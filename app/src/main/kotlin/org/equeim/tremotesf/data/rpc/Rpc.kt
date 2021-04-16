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

package org.equeim.tremotesf.data.rpc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.google.common.util.concurrent.ListenableFuture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
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

import org.equeim.tremotesf.Application
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.AppForegroundTracker.dropUntilInForeground
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentArgs
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.MutableEventFlow

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


typealias RpcConnectionState = org.equeim.libtremotesf.Rpc.Status
typealias RpcError = org.equeim.libtremotesf.Rpc.Error

data class ServerStats(var downloadSpeed: Long,
                       var uploadSpeed: Long,
                       var currentSession: SessionStats,
                       var total: SessionStats) {
    constructor() : this(0, 0, SessionStats(), SessionStats())
}

object Rpc : Logger {
    private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
    private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

    private val context = Application.instance

    private val scope = MainScope()

    init {
        LibTremotesf.init(javaClass.classLoader)
    }

    val nativeInstance: JniRpc = object : JniRpc() {
        override fun onStatusChanged(status: Int) {
            info("onStatusChanged() called with: status = $status")
            _connectionState.value = status
        }

        override fun onErrorChanged(error: Int, errorMessage: String) {
            info("onErrorChanged() called with: error = $error, errorMessage = $errorMessage")
            _error.value = Error(error, errorMessage)
        }

        override fun onServerSettingsChanged(data: JniServerSettingsData) {
            val old = serverSettings
            serverSettings = data
            old.delete()
        }

        override fun onTorrentsUpdated(removed: IntVector, changed: TorrentDataVector, added: TorrentDataVector) {
            Rpc.onTorrentsUpdated(removed, changed, added)
            removed.delete()
            changed.delete()
            added.delete()
        }

        override fun onServerStatsUpdated(downloadSpeed: Long, uploadSpeed: Long, currentSession: SessionStats, total: SessionStats) {
            _serverStats.value = ServerStats(downloadSpeed, uploadSpeed, currentSession, total)
        }

        override fun onTorrentAdded(id: Int, hashString: String, name: String) {
            info("onTorrentAdded() called with: id = $id, hashString = $hashString, name = $name")
            if (Settings.notifyOnAdded) {
                showAddedNotification(id, hashString, name)
            }
        }

        override fun onTorrentFinished(id: Int, hashString: String, name: String) {
            info("onTorrentFinished() called with: id = $id, hashString = $hashString, name = $name")
            if (Settings.notifyOnFinished) {
                showFinishedNotification(id, hashString, name)
            }
        }

        override fun onTorrentAddDuplicate() {
            info("onTorrentAddDuplicate() called")
            _torrentAddDuplicateEvents.tryEmit(Unit)
        }

        override fun onTorrentAddError() {
            info("onTorrentAddError() called")
            _torrentAddErrorEvents.tryEmit(Unit)
        }

        override fun onTorrentFilesUpdated(torrentId: Int, files: TorrentFilesVector) {
            onTorrentFilesUpdated(torrentId, files.toList())
            files.delete()
        }

        override fun onTorrentPeersUpdated(torrentId: Int, removed: IntVector, changed: TorrentPeersVector, added: TorrentPeersVector) {
            onTorrentPeersUpdated(torrentId, removed.toList(), changed.toList(), added.toList())
            removed.delete()
            changed.delete()
            added.delete()
        }

        override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
            info("onTorrentFileRenamed() called with: torrentId = $torrentId, filePath = $filePath, newName = $newName")
            _torrentFileRenamedEvents.tryEmit(TorrentFileRenamedData(torrentId, filePath, newName))
        }

        override fun onGotDownloadDirFreeSpace(bytes: Long) {
            info("onGotDownloadDirFreeSpace() called with: bytes = $bytes")
            _gotDownloadDirFreeSpaceEvent.tryEmit(bytes)
        }

        override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
            info("onGotFreeSpaceForPath() called with: path = $path, success = $success, bytes = $bytes")
            _gotFreeSpaceForPathEvents.tryEmit(GotFreeSpaceForPathData(path, success, bytes))
        }

        override fun onAboutToDisconnect() = Rpc.onAboutToDisconnect()
    }

    init {
        info("init: initializing native instance")
        nativeInstance.init()
        info("init: initialized native instance")
    }

    private val notificationManager: NotificationManager = context.getSystemService()!!

    private var updateWorkerCompleter = AtomicReference<CallbackToFutureAdapter.Completer<ListenableWorker.Result>>()

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
            .stateIn(GlobalScope + Dispatchers.Unconfined, SharingStarted.Eagerly, false)

    data class Error(val error: Int, val errorMessage: String)
    private val _error = MutableStateFlow(Error(RpcError.NoError, ""))
    val error: StateFlow<Error> by ::_error

    data class Status(val connectionState: Int = RpcConnectionState.Disconnected,
                      val error: Error = Error(RpcError.NoError, ""),
                      val statusString: String = "") {
        private companion object {
            fun getStatusString(connectionState: Int, error: Int): String {
                return when (connectionState) {
                    RpcConnectionState.Disconnected -> when (error) {
                        RpcError.NoError -> context.getString(R.string.disconnected)
                        RpcError.TimedOut -> context.getString(R.string.timed_out)
                        RpcError.ConnectionError -> context.getString(R.string.connection_error)
                        RpcError.AuthenticationError -> context.getString(R.string.authentication_error)
                        RpcError.ParseError -> context.getString(R.string.parsing_error)
                        RpcError.ServerIsTooNew -> context.getString(R.string.server_is_too_new)
                        RpcError.ServerIsTooOld -> context.getString(R.string.server_is_too_old)
                        else -> context.getString(R.string.disconnected)
                    }
                    RpcConnectionState.Connecting -> context.getString(R.string.connecting)
                    RpcConnectionState.Connected -> context.getString(R.string.connected)
                    else -> context.getString(R.string.disconnected)
                }
            }
        }

        constructor(connectionState: Int, error: Error) : this(connectionState, error, getStatusString(connectionState, error.error))

        val isConnected: Boolean
            get() = connectionState == RpcConnectionState.Connected
    }

    val status = combine(connectionState, error, ::Status)
            .stateIn(GlobalScope + Dispatchers.Unconfined, SharingStarted.Eagerly, Status())

    private val _torrentAddDuplicateEvents = MutableEventFlow<Unit>()
    val torrentAddDuplicateEvents: Flow<Unit> by ::_torrentAddDuplicateEvents

    private val _torrentAddErrorEvents = MutableEventFlow<Unit>()
    val torrentAddErrorEvents: Flow<Unit> by ::_torrentAddErrorEvents

    data class TorrentFilesUpdatedData(val torrentId: Int, val changedFiles: List<TorrentFile>)
    private val _torrentFilesUpdatedEvents = MutableEventFlow<TorrentFilesUpdatedData>()
    val torrentFilesUpdatedEvents: Flow<TorrentFilesUpdatedData> by ::_torrentFilesUpdatedEvents

    data class TorrentPeersUpdatedData(val torrentId: Int, val removed: List<Int>, val changed: List<Peer>, val added: List<Peer>)
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

    private val disconnectingAfterCurrentServerChanged = AtomicBoolean(false)

    private var connectedOnce = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannels(
                    listOf(NotificationChannel(FINISHED_NOTIFICATION_CHANNEL_ID,
                                               context.getString(R.string.finished_torrents_channel_name),
                                               NotificationManager.IMPORTANCE_DEFAULT),
                           NotificationChannel(ADDED_NOTIFICATION_CHANNEL_ID,
                                               context.getString(R.string.added_torrents_channel_name),
                                               NotificationManager.IMPORTANCE_DEFAULT))
            )
            info("init: created notification channels")
        }

        Servers.currentServer.value?.let(::setServer)

        scope.launch {
            Servers.currentServer.drop(1).collect { server ->
                info("Current server changed")

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

        scope.launch(Dispatchers.Unconfined) {
            connectionState.collect {
                when (it) {
                    RpcConnectionState.Connected -> {
                        withContext(Dispatchers.Main) {
                            showNotificationsSinceLastConnection()
                        }
                        handleWorkerCompleter()
                    }
                    RpcConnectionState.Disconnected -> {
                        if (disconnectingAfterCurrentServerChanged.compareAndSet(true, false)) {
                            info("Disconnected after current server changed")
                        } else {
                            handleWorkerCompleter()
                        }
                    }
                }
            }
        }

        AppForegroundTracker.appInForeground
            .dropUntilInForeground()
            .onEach(::onAppForegroundStateChanged)
            .launchIn(scope)

        WifiNetworkHelper.subscribeToForegroundTracker()

        info("init: finished initialization")
    }

    @AnyThread
    private fun setServer(server: Server) {
        info("setServer() called for server with: name = ${server.name}, address = ${server.address}, port = ${server.port}")

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
        info("connectOnce() called")
        if (!connectedOnce) {
            info("connectOnce: first connection")
            Servers.setCurrentServerFromWifiNetwork()
            nativeInstance.connect()
            connectedOnce = true
        } else {
            info("connectOnce: already connected once")
        }
    }

    @MainThread
    fun disconnectOnShutdown() {
        info("disconnectOnShutdown() called")
        nativeInstance.disconnect()
        connectedOnce = false
    }

    @MainThread
    private fun onAppForegroundStateChanged(inForeground: Boolean) {
        info("onAppForegroundStateChanged() called with: inForeground = $inForeground")
        if (inForeground) {
            connectOnce()
            cancelUpdateWorker()
        } else {
            enqueueUpdateWorker()
        }
        nativeInstance.setUpdateDisabled(!inForeground)
    }

    @WorkerThread
    private fun onTorrentsUpdated(removed: List<Int>, changed: List<TorrentData>, added: List<TorrentData>) {
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
                    torrentsIter.set(Torrent(changedTorrentData, context, torrent))
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
            newTorrents.add(Torrent(torrentData, context))
        }

        _torrents.value = newTorrents

        if (isConnected.value) {
            scope.launch(Dispatchers.Unconfined) { handleWorkerCompleter() }
        }
    }

    @MainThread
    private fun showNotificationsSinceLastConnection() {
        info("showNotificationsSinceLastConnection() called")
        val notifyOnFinished: Boolean
        val notifyOnAdded: Boolean
        if (updateWorkerCompleter.get() == null) {
            notifyOnFinished = Settings.notifyOnFinishedSinceLastConnection
            notifyOnAdded = Settings.notifyOnAddedSinceLastConnection
        } else {
            notifyOnFinished = Settings.notifyOnFinished
            notifyOnAdded = Settings.notifyOnAdded
        }

        if (!notifyOnFinished && !notifyOnAdded) {
            info("showNotificationsSinceLastConnection: notifications are disabled")
            return
        }

        val server = Servers.currentServer.value
        if (server == null) {
            error("showNotificationsSinceLastConnection: server is null")
            return
        }

        val lastTorrents = server.lastTorrents
        if (lastTorrents.saved) {
            for (torrent in torrents.value) {
                val hashString: String = torrent.hashString
                val oldTorrent = lastTorrents.torrents.find { it.hashString == hashString }
                if (oldTorrent == null) {
                    if (notifyOnAdded) {
                        showAddedNotification(torrent.id,
                            hashString,
                            torrent.name)
                    }
                } else {
                    if (!oldTorrent.finished && (torrent.isFinished) && notifyOnFinished) {
                        showFinishedNotification(torrent.id,
                            hashString,
                            torrent.name)
                    }
                }
            }
        }
    }

    @AnyThread
    private fun onTorrentFilesUpdated(torrentId: Int, files: List<TorrentFile>) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.filesEnabled) {
                _torrentFilesUpdatedEvents.tryEmit(TorrentFilesUpdatedData(torrentId, files))
            }
        }
    }

    @AnyThread
    private fun onTorrentPeersUpdated(torrentId: Int, removed: List<Int>, changed: List<Peer>, added: List<Peer>) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.peersEnabled) {
                _torrentPeersUpdatedEvents.tryEmit(TorrentPeersUpdatedData(torrentId, removed, changed, added))
            }
        }
    }

    @AnyThread
    private fun onAboutToDisconnect() {
        info("onAboutToDisconnect() called")
        if (!disconnectingAfterCurrentServerChanged.get()) {
            info("onAboutToDisconnect: saving servers")
            Servers.save()
        } else {
            info("onAboutToDisconnect: current server changed, do nothing")
        }
    }

    @AnyThread
    private fun showTorrentNotification(torrentId: Int,
                                        hashString: String,
                                        name: String,
                                        notificationChannel: String,
                                        notificationTitle: String) {
        info("showTorrentNotification() called with: torrentId = $torrentId, hashString = $hashString, name = $name, notificationChannel = $notificationChannel, notificationTitle = $notificationTitle")
        notificationManager.notify(
                torrentId,
                NotificationCompat.Builder(context, notificationChannel)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(notificationTitle)
                        .setContentText(name)
                        .setContentIntent(NavDeepLinkBuilder(context)
                                                  .setGraph(R.navigation.nav_main)
                                                  .setDestination(R.id.torrent_properties_fragment)
                                                  .setArguments(TorrentPropertiesFragmentArgs(hashString, name).toBundle())
                                                  .createPendingIntent())
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .build())
    }

    @AnyThread
    private fun showFinishedNotification(id: Int, hashString: String, name: String) {
        showTorrentNotification(id,
                                hashString,
                                name,
                                FINISHED_NOTIFICATION_CHANNEL_ID,
                                context.getString(R.string.torrent_finished))
    }

    @AnyThread
    private fun showAddedNotification(id: Int, hashString: String, name: String) {
        showTorrentNotification(id,
                                hashString,
                                name,
                                ADDED_NOTIFICATION_CHANNEL_ID,
                                context.getString(R.string.torrent_added))
    }

    @AnyThread
    private fun enqueueUpdateWorker() {
        info("enqueueUpdateWorker() called")
        val interval = Settings.backgroundUpdateInterval
        if (interval > 0 && (Settings.notifyOnFinished || Settings.notifyOnAdded)) {
            info("enqueueUpdateWorker: enqueueing worker, interval = $interval minutes")
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequest.Builder(UpdateWorker::class.java, interval, TimeUnit.MINUTES)
                    .setInitialDelay(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UpdateWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request).state.observeForever { state ->
                if (state !is Operation.State.IN_PROGRESS) {
                    info("enqueueUpdateWorker: enqueuing worker result = $state")
                }
            }
        } else {
            info("enqueueUpdateWorker: not enqueueing worker, disabled in settings")
        }
    }

    @AnyThread
    private fun cancelUpdateWorker() {
        info("cancelUpdateWorker() called")
        WorkManager.getInstance(context).cancelUniqueWork(UpdateWorker.UNIQUE_WORK_NAME).state.observeForever { state ->
            if (state !is Operation.State.IN_PROGRESS) {
                info("cancelUpdateWorker: cancelling worker result = $state")
            }
        }
    }

    @AnyThread
    private suspend fun handleWorkerCompleter() {
        updateWorkerCompleter.getAndSet(null)?.let { completer ->
            info("handleWorkerCompleter: completing update worker")
            if (isConnected.value) {
                info("handleWorkerCompleter: save servers")
                withContext(Dispatchers.Main) {
                    if (isConnected.value) {
                        Servers.save()
                    }
                }
            }
            info("handleWorkerCompleter: setting worker result")
            completer.set(ListenableWorker.Result.success())
        }
    }

    class UpdateWorker(context: Context, workerParameters: WorkerParameters) : ListenableWorker(context, workerParameters), Logger {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override fun startWork(): ListenableFuture<Result> {
            info("startWork() called")

            if (AppForegroundTracker.appInForeground.value) {
                warn("startWork: app is in foreground, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!Servers.hasServers) {
                warn("startWork: no servers, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!Settings.notifyOnFinished && !Settings.notifyOnAdded) {
                warn("startWork:, notifications are disabled, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            return CallbackToFutureAdapter.getFuture { completer ->
                updateWorkerCompleter.getAndSet(completer)?.set(Result.success())
                if (!Servers.setCurrentServerFromWifiNetwork()) {
                    if (connectionState.value == RpcConnectionState.Disconnected) {
                        nativeInstance.connect()
                    } else {
                        nativeInstance.updateData()
                    }
                }
                javaClass.simpleName
            }
        }

        override fun onStopped() {
            info("onStopped() called")
        }
    }
}
