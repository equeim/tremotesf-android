/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import java.util.concurrent.TimeUnit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.google.common.util.concurrent.ListenableFuture

import org.qtproject.qt5.android.QtNative

import org.equeim.libtremotesf.JniRpc
import org.equeim.libtremotesf.JniServerSettings
import org.equeim.libtremotesf.IntVector
import org.equeim.libtremotesf.Peer
import org.equeim.libtremotesf.ServerStats
import org.equeim.libtremotesf.TorrentData
import org.equeim.libtremotesf.TorrentDataVector
import org.equeim.libtremotesf.TorrentFile
import org.equeim.libtremotesf.TorrentFilesVector
import org.equeim.libtremotesf.TorrentPeersVector
import org.equeim.tremotesf.torrentpropertiesfragment.TorrentPropertiesFragment
import org.equeim.tremotesf.utils.LiveEvent
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.NonNullMutableLiveData
import org.equeim.tremotesf.utils.emit


typealias RpcStatus = org.equeim.libtremotesf.Rpc.Status
typealias RpcError = org.equeim.libtremotesf.Rpc.Error

object Rpc : Logger {
    private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
    private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

    private val context = Application.instance

    private val handler = Handler(Looper.getMainLooper())

    init {
        System.loadLibrary("c++_shared")
        QtNative.setClassLoader(context.classLoader)
        System.loadLibrary("Qt5Core")
        System.loadLibrary("Qt5Network")
        System.loadLibrary("tremotesf")
    }

    val nativeInstance: JniRpc = object : JniRpc() {
        private fun <T: Any> moveFromNativeVector(vector: MutableList<T>): List<T> {
            val list = mutableListOf<T>()
            for (i in (vector.size - 1) downTo 0) {
                list.add(vector.removeAt(i))
            }
            list.reverse()
            return list
        }

        override fun onStatusChanged(status: Int) {
            handler.post {
                Rpc.onStatusChanged(status)
            }
        }

        override fun onErrorChanged(error: Int, errorMessage: String) {
            handler.post {
                Rpc.onErrorChanged(error, errorMessage)
            }
        }

        override fun onTorrentsUpdated(removed: IntVector, changed: TorrentDataVector, added: TorrentDataVector) {
            val r = removed.toList()
            val c = moveFromNativeVector(changed)
            val a = moveFromNativeVector(added)
            handler.post {
                Rpc.onTorrentsUpdated(r, c, a)
            }
        }

        override fun onServerStatsUpdated() {
            handler.post {
                Rpc.onServerStatsUpdated()
            }
        }

        override fun onTorrentAdded(id: Int, hashString: String, name: String) {
            handler.post {
                Rpc.onTorrentAdded(id, hashString, name)
            }
        }

        override fun onTorrentFinished(id: Int, hashString: String, name: String) {
            handler.post {
                Rpc.onTorrentFinished(id, hashString, name)
            }
        }

        override fun onTorrentAddDuplicate() {
            handler.post {
                Rpc.onTorrentAddDuplicate()
            }
        }

        override fun onTorrentAddError() {
            handler.post {
                Rpc.onTorrentAddError()
            }
        }

        override fun onTorrentFilesUpdated(torrentId: Int, files: TorrentFilesVector) {
            val list = moveFromNativeVector(files)
            handler.post {
                Rpc.onTorrentFilesUpdated(torrentId, list)
            }
        }

        override fun onTorrentPeersUpdated(torrentId: Int, removed: IntVector, changed: TorrentPeersVector, added: TorrentPeersVector) {
            val r = removed.toList()
            val c = moveFromNativeVector(changed)
            val a = moveFromNativeVector(added)
            handler.post {
                Rpc.onTorrentPeersUpdated(torrentId, r, c, a)
            }
        }

        override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
            handler.post {
                Rpc.onTorrentFileRenamed(torrentId, filePath, newName)
            }
        }

        override fun onGotDownloadDirFreeSpace(bytes: Long) {
            handler.post {
                Rpc.onGotDownloadDirFreeSpace(bytes)
            }
        }

        override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
            handler.post {
                Rpc.onGotFreeSpaceForPath(path, success, bytes)
            }
        }

        override fun onAboutToDisconnect() {
            handler.post {
                Rpc.onAboutToDisconnect()
            }
        }
    }

    private val notificationManager: NotificationManager = context.getSystemService()!!

    private var updateWorkerCompleter: CallbackToFutureAdapter.Completer<ListenableWorker.Result>? = null

    val serverSettings: JniServerSettings = nativeInstance.serverSettings()
    val serverStats = NonNullMutableLiveData<ServerStats>(nativeInstance.serverStats())

    val status = NonNullMutableLiveData(RpcStatus.Disconnected)

    val isConnected: Boolean
        get() = (status.value == RpcStatus.Connected)

    val statusString: String
        get() {
            return when (status.value) {
                RpcStatus.Disconnected -> when (error.value) {
                    RpcError.NoError -> context.getString(R.string.disconnected)
                    RpcError.TimedOut -> context.getString(R.string.timed_out)
                    RpcError.ConnectionError -> context.getString(R.string.connection_error)
                    RpcError.AuthenticationError -> context.getString(R.string.authentication_error)
                    RpcError.ParseError -> context.getString(R.string.parsing_error)
                    RpcError.ServerIsTooNew -> context.getString(R.string.server_is_too_new)
                    RpcError.ServerIsTooOld -> context.getString(R.string.server_is_too_old)
                    else -> context.getString(R.string.disconnected)
                }
                RpcStatus.Connecting -> context.getString(R.string.connecting)
                RpcStatus.Connected -> context.getString(R.string.connected)
                else -> context.getString(R.string.disconnected)
            }
        }

    val error = NonNullMutableLiveData(RpcError.NoError)
    var errorMessage: String = ""
        private set

    val torrentAddDuplicateEvent = LiveEvent<Unit>()
    val torrentAddErrorEvent = LiveEvent<Unit>()

    data class TorrentFilesUpdatedData(val torrentId: Int, val changedFiles: List<TorrentFile>)
    val torrentFilesUpdatedEvent = LiveEvent<TorrentFilesUpdatedData>()
    data class TorrentPeersUpdatedData(val torrentId: Int, val removed: List<Int>, val changed: List<Peer>, val added: List<Peer>)
    val torrentPeersUpdatedEvent = LiveEvent<TorrentPeersUpdatedData>()

    data class TorrentFileRenamedData(val torrentId: Int, val filePath: String, val newName: String)
    val torrentFileRenamedEvent = LiveEvent<TorrentFileRenamedData>()

    val gotDownloadDirFreeSpaceEvent = LiveEvent<Long>()
    data class GotFreeSpaceForPathData(val path: String, val success: Boolean, val bytes: Long)
    val gotFreeSpaceForPathEvent = LiveEvent<GotFreeSpaceForPathData>()

    val torrents = NonNullMutableLiveData<List<Torrent>>(emptyList())

    private var disconnectingAfterCurrentServerChanged = false

    private var connectedOnce = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannels(listOf(NotificationChannel(FINISHED_NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.finished_torrents_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(ADDED_NOTIFICATION_CHANNEL_ID,
                            context.getString(R.string.added_torrents_channel_name),
                            NotificationManager.IMPORTANCE_DEFAULT)))
        }

        var gotFirst = false
        Servers.currentServer.observeForever { server ->
            if (!gotFirst) {
                gotFirst = true
                return@observeForever
            }

            if (isConnected) {
                disconnectingAfterCurrentServerChanged = true
            }

            if (server != null) {
                setServer(server)
                nativeInstance.connect()
            } else {
                nativeInstance.resetServer()
            }
        }

        Servers.currentServer.value?.let(::setServer)
    }

    private fun setServer(server: Server) {
        nativeInstance.setServer(
                server.name,
                server.address,
                server.port,
                server.apiPath,
                server.httpsEnabled,
                server.selfSignedCertificateEnabled,
                server.selfSignedCertificate.toByteArray(),
                server.clientCertificateEnabled,
                server.clientCertificate.toByteArray(),
                server.authentication,
                server.username,
                server.password,
                server.updateInterval,
                0,
                server.timeout
        )
    }

    fun connectOnce() {
        if (!connectedOnce) {
            nativeInstance.connect()
            connectedOnce = true
        }
    }

    fun disconnectOnShutdown() {
        nativeInstance.disconnect()
        connectedOnce = false
    }

    private fun onStatusChanged(newStatus: Int) {
        status.value = newStatus
        when (newStatus) {
            RpcStatus.Connected -> {
                showNotificationsSinceLastConnection()
                handleWorkerCompleter()
            }
            RpcStatus.Disconnected -> handleWorkerCompleter()
        }
    }

    private fun onErrorChanged(newError: Int, newErrorMessage: String) {
        errorMessage = newErrorMessage
        error.value = newError
    }

    private fun onTorrentsUpdated(removed: List<Int>, changed: List<TorrentData>, added: List<TorrentData>) {
        val newTorrents = torrents.value.toMutableList()

        for (index in removed) {
            newTorrents.removeAt(index)
        }

        if (changed.isNotEmpty()) {
            val changedIter = changed.iterator()
            var changedTorrentData = changedIter.next()
            var changedId = changedTorrentData.id
            for (torrent in newTorrents) {
                if (torrent.id == changedId) {
                    torrent.update(changedTorrentData)
                    if (changedIter.hasNext()) {
                        changedTorrentData = changedIter.next()
                        changedId = changedTorrentData.id
                    } else {
                        torrent.changed = false
                    }
                }
            }
        }

        for (torrentData in added) {
            newTorrents.add(Torrent(torrentData, context))
        }

        torrents.value = newTorrents

        if (isConnected) {
            handleWorkerCompleter()
        }
    }

    private fun showNotificationsSinceLastConnection() {
        val notifyOnFinished: Boolean
        val notifyOnAdded: Boolean
        if (updateWorkerCompleter == null) {
            notifyOnFinished = Settings.notifyOnFinishedSinceLastConnection
            notifyOnAdded = Settings.notifyOnAddedSinceLastConnection
        } else {
            notifyOnFinished = Settings.notifyOnFinished
            notifyOnAdded = Settings.notifyOnAdded
        }

        if (notifyOnFinished || notifyOnAdded) {
            val server = Servers.currentServer.value
            if (server != null) {
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
        }
    }

    private fun onServerStatsUpdated() {
        serverStats.value = serverStats.value
    }

    private fun onTorrentAdded(id: Int, hashString: String, name: String) {
        if (Settings.notifyOnAdded) {
            showAddedNotification(id, hashString, name)
        }
    }

    private fun onTorrentFinished(id: Int, hashString: String, name: String) {
        if (Settings.notifyOnFinished) {
            showFinishedNotification(id, hashString, name)
        }
    }

    private fun onTorrentAddDuplicate() {
        torrentAddDuplicateEvent.emit()
    }

    private fun onTorrentAddError() {
        torrentAddErrorEvent.emit()
    }

    private fun onTorrentFilesUpdated(torrentId: Int, files: List<TorrentFile>) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.filesEnabled) {
                torrent.filesLoaded = true
                torrentFilesUpdatedEvent.emit(TorrentFilesUpdatedData(torrentId, files))
            }
        }
    }

    private fun onTorrentPeersUpdated(torrentId: Int, removed: List<Int>, changed: List<Peer>, added: List<Peer>) {
        torrents.value.find { it.id == torrentId }?.let { torrent ->
            if (torrent.peersEnabled) {
                torrent.peersLoaded = true
                torrentPeersUpdatedEvent.emit(TorrentPeersUpdatedData(torrentId, removed, changed, added))
            }
        }
    }

    private fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
        torrentFileRenamedEvent.emit(TorrentFileRenamedData(torrentId, filePath, newName))
    }

    private fun onGotDownloadDirFreeSpace(bytes: Long) {
        gotDownloadDirFreeSpaceEvent.emit(bytes)
    }

    private fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
        gotFreeSpaceForPathEvent.emit(GotFreeSpaceForPathData(path, success, bytes))
    }

    private fun onAboutToDisconnect() {
        if (disconnectingAfterCurrentServerChanged) {
            disconnectingAfterCurrentServerChanged = false
        } else {
            Servers.save()
        }
    }

    private fun showTorrentNotification(torrentId: Int,
                                        hashString: String,
                                        name: String,
                                        notificationChannel: String,
                                        notificationTitle: String) {
        notificationManager.notify(
                torrentId,
                NotificationCompat.Builder(context, notificationChannel)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(notificationTitle)
                        .setContentText(name)
                        .setContentIntent(NavDeepLinkBuilder(context)
                                                  .setGraph(R.navigation.nav_main)
                                                  .setDestination(R.id.torrentPropertiesFragment)
                                                  .setArguments(bundleOf(TorrentPropertiesFragment.HASH to hashString,
                                                                         TorrentPropertiesFragment.NAME to name))
                                                  .createPendingIntent())
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .build())
    }

    private fun showFinishedNotification(id: Int, hashString: String, name: String) {
        showTorrentNotification(id,
                hashString,
                name,
                FINISHED_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.torrent_finished))
    }

    private fun showAddedNotification(id: Int, hashString: String, name: String) {
        showTorrentNotification(id,
                hashString,
                name,
                ADDED_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.torrent_added))
    }

    fun enqueueUpdateWorker() {
        val interval = Settings.backgroundUpdateInterval
        if (interval > 0 && (Settings.notifyOnFinished || Settings.notifyOnAdded)) {
            info("Rpc.enqueueUpdateWorker(), interval=$interval")
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequest.Builder(UpdateWorker::class.java, interval, TimeUnit.MINUTES)
                    .setInitialDelay(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UpdateWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun cancelUpdateWorker() {
        info("Rpc.cancelUpdateWorker()")
        WorkManager.getInstance(context).cancelUniqueWork(UpdateWorker.UNIQUE_WORK_NAME)
    }

    private fun handleWorkerCompleter() {
        updateWorkerCompleter?.let { completer ->
            info("Rpc.handleWorkerCompleter()")
            if (isConnected) {
                Servers.save()
            }
            completer.set(ListenableWorker.Result.success())
            updateWorkerCompleter = null
        }
    }

    class UpdateWorker(context: Context, workerParameters: WorkerParameters) : ListenableWorker(context, workerParameters), Logger {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override fun startWork(): ListenableFuture<Result> {
            info("Rpc.UpdateWorker.startWork()")

            if (NavigationActivity.activeActivity != null) {
                warn("Rpc.UpdateWorker.startWork(), activity is not null, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!Settings.notifyOnFinished && !Settings.notifyOnAdded) {
                warn("Rpc.UpdateWorker.startWork(), notifications are disabled, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            return CallbackToFutureAdapter.getFuture { completer ->
                updateWorkerCompleter = completer
                if (status.value == RpcStatus.Disconnected) {
                    nativeInstance.connect()
                } else {
                    nativeInstance.updateData()
                }
                javaClass.simpleName
            }
        }
    }
}
