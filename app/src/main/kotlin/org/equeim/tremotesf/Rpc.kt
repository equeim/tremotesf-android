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
import android.app.PendingIntent
import android.content.Context
import android.os.Build

import androidx.concurrent.futures.CallbackToFutureAdapter

import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.google.common.util.concurrent.ListenableFuture

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.warn

import org.qtproject.qt5.android.QtNative

import org.equeim.libtremotesf.JniRpc
import org.equeim.libtremotesf.JniServerSettings
import org.equeim.libtremotesf.ServerStats
import org.equeim.libtremotesf.Torrent
import org.equeim.libtremotesf.TorrentsVector
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity


typealias RpcStatus = org.equeim.libtremotesf.Rpc.Status
typealias RpcError = org.equeim.libtremotesf.Rpc.Error

object Rpc : AnkoLogger {
    private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
    private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

    private val context = Application.instance

    init {
        System.loadLibrary("c++_shared")
        QtNative.setClassLoader(context.classLoader)
        System.loadLibrary("Qt5Core")
        System.loadLibrary("Qt5Network")
        System.loadLibrary("tremotesf")
    }

    val nativeInstance: JniRpc = object : JniRpc(), AnkoLogger {
        override fun onStatusChanged(status: Int) {
            context.runOnUiThread {
                Rpc.onStatusChanged(status)
            }
        }

        override fun onErrorChanged(error: Int, errorMessage: String) {
            context.runOnUiThread {
                Rpc.onErrorChanged(error, errorMessage)
            }
        }

        override fun onTorrentsUpdated(torrents: TorrentsVector) {
            val list = torrents.toList()
            context.runOnUiThread {
                onTorrentsUpdated(list)
            }
        }

        override fun onServerStatsUpdated() {
            context.runOnUiThread {
                Rpc.onServerStatsUpdated()
            }
        }

        override fun onTorrentAdded(id: Int, hashString: String, name: String) {
            context.runOnUiThread {
                Rpc.onTorrentAdded(id, hashString, name)
            }
        }

        override fun onTorrentFinished(id: Int, hashString: String, name: String) {
            context.runOnUiThread {
                Rpc.onTorrentFinished(id, hashString, name)
            }
        }

        override fun onTorrentAddDuplicate() {
            context.runOnUiThread {
                Rpc.onTorrentAddDuplicate()
            }
        }

        override fun onTorrentAddError() {
            context.runOnUiThread {
                Rpc.onTorrentAddError()
            }
        }

        override fun onGotTorrentFiles(torrentId: Int) {
            context.runOnUiThread {
                Rpc.onGotTorrentFiles(torrentId)
            }
        }

        override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
            context.runOnUiThread {
                Rpc.onTorrentFileRenamed(torrentId, filePath, newName)
            }
        }

        override fun onGotTorrentPeers(torrentId: Int) {
            context.runOnUiThread {
                Rpc.onGotTorrentPeers(torrentId)
            }
        }

        override fun onGotDownloadDirFreeSpace(bytes: Long) {
            context.runOnUiThread {
                Rpc.onGotDownloadDirFreeSpace(bytes)
            }
        }

        override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
            context.runOnUiThread {
                Rpc.onGotFreeSpaceForPath(path, success, bytes)
            }
        }

        override fun onAboutToDisconnect() {
            context.runOnUiThread {
                Rpc.onAboutToDisconnect()
            }
        }
    }

    private val notificationManager: NotificationManager = context.getSystemService()!!

    private var updateWorkerCompleter: CallbackToFutureAdapter.Completer<ListenableWorker.Result>? = null

    val serverSettings: JniServerSettings = nativeInstance.serverSettings()
    val serverStats: ServerStats = nativeInstance.serverStats()

    var status: Int = RpcStatus.Disconnected
        private set

    val isConnected: Boolean
        get() = (status == RpcStatus.Connected)

    val statusString: String
        get() {
            return when (status) {
                RpcStatus.Disconnected -> when (error) {
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

    var error: Int = RpcError.NoError
        private set
    var errorMessage: String = ""
        private set

    private val statusListeners = mutableListOf<(Int) -> Unit>()
    private val errorListeners = mutableListOf<(Int) -> Unit>()
    private val torrentsUpdatedListeners = mutableListOf<() -> Unit>()
    private val serverStatsUpdatedListeners = mutableListOf<() -> Unit>()

    var torrentAddDuplicateListener: (() -> Unit)? = null
    var torrentAddErrorListener: (() -> Unit)? = null

    var gotTorrentFilesListener: ((Int) -> Unit)? = null
    var torrentFileRenamedListener: ((Int, String, String) -> Unit)? = null

    var gotTorrentPeersListener: ((Int) -> Unit)? = null

    var gotDownloadDirFreeSpaceListener: ((Long) -> Unit)? = null
    var gotFreeSpaceForPathListener: ((String, Boolean, Long) -> Unit)? = null

    val torrents = mutableListOf<TorrentData>()
    private var firstTorrentsAfterConnection = false

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

        Servers.addCurrentServerListener {
            if (isConnected) {
                disconnectingAfterCurrentServerChanged = true
            }

            val server = Servers.currentServer
            if (server != null) {
                setServer(server)
                nativeInstance.connect()
            } else {
                nativeInstance.resetServer()
            }
        }

        Servers.currentServer?.let { setServer(it) }
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

    fun addStatusListener(listener: (Int) -> Unit) = statusListeners.add(listener)
    fun removeStatusListener(listener: (Int) -> Unit) = statusListeners.remove(listener)

    private fun onStatusChanged(newStatus: Int) {
        status = newStatus
        for (listener in statusListeners) {
            listener(status)
        }
        when (status) {
            RpcStatus.Connected -> firstTorrentsAfterConnection = true
            RpcStatus.Disconnected -> handleWorkerCompleter()
        }
    }

    fun addErrorListener(listener: (Int) -> Unit) = errorListeners.add(listener)
    fun removeErrorListener(listener: (Int) -> Unit) = errorListeners.remove(listener)

    private fun onErrorChanged(newError: Int, newErrorMessage: String) {
        error = newError
        errorMessage = newErrorMessage
        for (listener in errorListeners) {
            listener(error)
        }
        if (error == RpcError.ConnectionError) {
            BaseActivity.showToast(errorMessage)
        }
    }

    fun addTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.add(listener)
    fun removeTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.remove(listener)

    private fun onTorrentsUpdated(newTorrents: List<Torrent>) {
        val oldTorrents = torrents.toList()
        torrents.clear()
        for (torrent in newTorrents) {
            val id = torrent.id()
            val data = oldTorrents.find { it.id == id }
            if (data == null) {
                torrents.add(TorrentData(id, torrent, context))
            } else {
                torrents.add(data)
                data.update()
            }
        }

        for (listener in torrentsUpdatedListeners) {
            listener()
        }

        if (firstTorrentsAfterConnection) {
            showNotificationsSinceLastConnection()
            firstTorrentsAfterConnection = false
        }
        handleWorkerCompleter()
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
            val server = Servers.currentServer
            if (server != null) {
                val lastTorrents = server.lastTorrents
                if (lastTorrents.saved) {
                    for (torrent in torrents) {
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

    fun addServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.add(listener)
    fun removeServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.remove(listener)

    private fun onServerStatsUpdated() {
        for (listener in serverStatsUpdatedListeners) {
            listener()
        }
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
        torrentAddDuplicateListener?.invoke()
    }

    private fun onTorrentAddError() {
        torrentAddErrorListener?.invoke()
    }

    private fun onGotTorrentFiles(torrentId: Int) {
        context.runOnUiThread {
            gotTorrentFilesListener?.invoke(torrentId)
        }
    }

    private fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
        torrentFileRenamedListener?.invoke(torrentId, filePath, newName)
    }

    private fun onGotTorrentPeers(torrentId: Int) {
        gotTorrentPeersListener?.invoke(torrentId)
    }

    private fun onGotDownloadDirFreeSpace(bytes: Long) {
        gotDownloadDirFreeSpaceListener?.invoke(bytes)
    }

    private fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
        gotFreeSpaceForPathListener?.invoke(path, success, bytes)
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
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(TorrentPropertiesActivity::class.java)
        stackBuilder.addNextIntent(context.intentFor<TorrentPropertiesActivity>(TorrentPropertiesActivity.HASH to hashString,
                TorrentPropertiesActivity.NAME to name))

        notificationManager.notify(
                torrentId,
                NotificationCompat.Builder(context, notificationChannel)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(notificationTitle)
                        .setContentText(name)
                        .setContentIntent(stackBuilder.getPendingIntent(0,
                                PendingIntent.FLAG_UPDATE_CURRENT))
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

    class UpdateWorker(context: Context, workerParameters: WorkerParameters) : ListenableWorker(context, workerParameters), AnkoLogger {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override fun startWork(): ListenableFuture<Result> {
            info("Rpc.UpdateWorker.startWork()")

            if (BaseActivity.activeActivity != null) {
                warn("Rpc.UpdateWorker.startWork(), activity is not null, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!Settings.notifyOnFinished && !Settings.notifyOnAdded) {
                warn("Rpc.UpdateWorker.startWork(), notifications are disabled, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            return CallbackToFutureAdapter.getFuture { completer ->
                updateWorkerCompleter = completer
                if (status == RpcStatus.Disconnected) {
                    nativeInstance.connect()
                } else {
                    nativeInstance.updateData()
                }
                javaClass.simpleName
            }
        }
    }
}

// Torrent extension methods

fun Torrent.setDownloadSpeedLimited(limited: Boolean) {
    Rpc.nativeInstance.setTorrentDownloadSpeedLimited(this, limited)
}

fun Torrent.setDownloadSpeedLimit(limit: Int) {
    Rpc.nativeInstance.setTorrentDownloadSpeedLimit(this, limit)
}

fun Torrent.setUploadSpeedLimited(limited: Boolean) {
    Rpc.nativeInstance.setTorrentUploadSpeedLimited(this, limited)
}

fun Torrent.setUploadSpeedLimit(limit: Int) {
    Rpc.nativeInstance.setTorrentUploadSpeedLimit(this, limit)
}

fun Torrent.setRatioLimitMode(mode: Int) {
    Rpc.nativeInstance.setTorrentRatioLimitMode(this, mode)
}

fun Torrent.setRatioLimit(limit: Double) {
    Rpc.nativeInstance.setTorrentRatioLimit(this, limit)
}

fun Torrent.setPeersLimit(limit: Int) {
    Rpc.nativeInstance.setTorrentPeersLimit(this, limit)
}

fun Torrent.setHonorSessionLimits(honor: Boolean) {
    Rpc.nativeInstance.setTorrentHonorSessionLimits(this, honor)
}

fun Torrent.setBandwidthPriority(priority: Int) {
    Rpc.nativeInstance.setTorrentBandwidthPriority(this, priority)
}

fun Torrent.setIdleSeedingLimitMode(mode: Int) {
    Rpc.nativeInstance.setTorrentIdleSeedingLimitMode(this, mode)
}

fun Torrent.setIdleSeedingLimit(limit: Int) {
    Rpc.nativeInstance.setTorrentIdleSeedingLimit(this, limit)
}

fun Torrent.setFilesEnabled(enabled: Boolean) {
    Rpc.nativeInstance.setTorrentFilesEnabled(this, enabled)
}

fun Torrent.setFilesWanted(files: IntArray, wanted: Boolean) {
    Rpc.nativeInstance.setTorrentFilesWanted(this, files, wanted)
}

fun Torrent.setFilesPriority(files: IntArray, priority: Int) {
    Rpc.nativeInstance.setTorrentFilesPriority(this, files, priority)
}

fun Torrent.addTracker(announce: String) {
    Rpc.nativeInstance.torrentAddTracker(this, announce)
}

fun Torrent.setTracker(trackerId: Int, announce: String) {
    Rpc.nativeInstance.torrentSetTracker(this, trackerId, announce)
}

fun Torrent.removeTrackers(ids: IntArray) {
    Rpc.nativeInstance.torrentRemoveTrackers(this, ids)
}

fun Torrent.setPeersEnabled(enabled: Boolean) {
    Rpc.nativeInstance.setTorrentPeersEnabled(this, enabled)
}