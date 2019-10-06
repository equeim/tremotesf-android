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

import org.equeim.libtremotesf.JniRpc
import org.equeim.libtremotesf.JniServerSettings
import org.equeim.libtremotesf.ServerStats
import org.equeim.libtremotesf.Torrent
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity

class Rpc : JniRpc(), AnkoLogger {
    companion object {
        private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
        private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

        val instance by lazy { Rpc() }
    }

    var context: Context? = null
        set(value) {
            field = value
            if (value == null) {
                notificationManager = null
            } else {
                notificationManager = value.getSystemService()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager?.createNotificationChannels(listOf(NotificationChannel(FINISHED_NOTIFICATION_CHANNEL_ID,
                                                                                               value.getString(R.string.finished_torrents_channel_name),
                                                                                               NotificationManager.IMPORTANCE_DEFAULT),
                                                                           NotificationChannel(ADDED_NOTIFICATION_CHANNEL_ID,
                                                                                               value.getString(R.string.added_torrents_channel_name),
                                                                                               NotificationManager.IMPORTANCE_DEFAULT)))
                }

                updateServer()
            }
        }
    private var notificationManager: NotificationManager? = null
    private var updateWorkerCompleter: CallbackToFutureAdapter.Completer<ListenableWorker.Result>? = null

    val serverSettings: JniServerSettings = serverSettings()
    val serverStats: ServerStats = serverStats()

    val statusString: String
        get() {
            return when (status()) {
                Status.Disconnected -> when (error()) {
                    Error.NoError -> context!!.getString(R.string.disconnected)
                    Error.TimedOut -> context!!.getString(R.string.timed_out)
                    Error.ConnectionError -> context!!.getString(R.string.connection_error)
                    Error.AuthenticationError -> context!!.getString(R.string.authentication_error)
                    Error.ParseError -> context!!.getString(R.string.parsing_error)
                    Error.ServerIsTooNew -> context!!.getString(R.string.server_is_too_new)
                    Error.ServerIsTooOld -> context!!.getString(R.string.server_is_too_old)
                    else -> context!!.getString(R.string.disconnected)
                }
                Status.Connecting -> context!!.getString(R.string.connecting)
                Status.Connected -> context!!.getString(R.string.connected)
                else -> context!!.getString(R.string.disconnected)
            }
        }

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

    private var disconnectingAfterCurrentServerChanged = false

    init {
        Servers.addCurrentServerListener {
            if (isConnected) {
                disconnectingAfterCurrentServerChanged = true
            }
            if (Servers.hasServers) {
                updateServer()
                connect()
            } else {
                resetServer()
            }
        }
    }

    private fun updateServer() {
        if (Servers.hasServers) {
            val server = Servers.currentServer!!
            setServer(server.name,
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
                      server.timeout)
        } else {
            resetServer()
        }
    }

    fun addStatusListener(listener: (Int) -> Unit) = statusListeners.add(listener)
    fun removeStatusListener(listener: (Int) -> Unit) = statusListeners.remove(listener)

    override fun onConnectedChanged() {
        context!!.runOnUiThread {
            if (isConnected) {
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
                            val torrents = torrents()
                            for (torrent: Torrent in torrents) {
                                val hashString: String = torrent.hashString()
                                val oldTorrent = lastTorrents.torrents.find { it.hashString == hashString }
                                if (oldTorrent == null) {
                                    if (notifyOnAdded) {
                                        showAddedNotification(torrent.id(),
                                                              hashString,
                                                              torrent.name(),
                                                              context!!)
                                    }
                                } else {
                                    if (!oldTorrent.finished && (torrent.isFinished) && notifyOnFinished) {
                                        showFinishedNotification(torrent.id(),
                                                                 hashString,
                                                                 torrent.name(),
                                                                 context!!)
                                    }
                                }
                            }
                        }
                    }
                }

                handleWorkerCompleter(true)
            }
        }
    }

    override fun onStatusChanged() {
        context!!.runOnUiThread {
            val s = status()
            for (listener in statusListeners) {
                listener(s)
            }
            if (s == Status.Disconnected) {
                handleWorkerCompleter(false)
            }
        }
    }

    fun addErrorListener(listener: (Int) -> Unit) = errorListeners.add(listener)
    fun removeErrorListener(listener: (Int) -> Unit) = errorListeners.remove(listener)

    override fun onErrorChanged() {
        context!!.runOnUiThread {
            val error = error()
            for (listener in errorListeners) {
                listener(error)
            }
            if (error == Error.ConnectionError) {
                BaseActivity.showToast(errorMessage())
            }
        }
    }

    fun addTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.add(listener)
    fun removeTorrentsUpdatedListener(listener: () -> Unit) = torrentsUpdatedListeners.remove(listener)

    override fun onTorrentsUpdated() {
        context!!.runOnUiThread {
            val oldTorrents = torrents.toList()
            torrents.clear()
            val rpcTorrents = torrents()
            for (torrent: Torrent in rpcTorrents) {
                val id = torrent.id()
                val data = oldTorrents.find { it.id == id }
                if (data == null) {
                    torrents.add(TorrentData(torrent, context!!))
                } else {
                    torrents.add(data)
                    data.update()
                }
            }

            for (listener in torrentsUpdatedListeners) {
                listener()
            }

            handleWorkerCompleter(true)
        }
    }

    fun addServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.add(listener)
    fun removeServerStatsUpdatedListener(listener: () -> Unit) = serverStatsUpdatedListeners.remove(listener)

    override fun onServerStatsUpdated() {
        context!!.runOnUiThread {
            for (listener in serverStatsUpdatedListeners) {
                listener()
            }
        }
    }

    override fun onTorrentAdded(id: Int, hashString: String, name: String) {
        context!!.runOnUiThread {
            if (Settings.notifyOnAdded) {
                showAddedNotification(id, hashString, name, this)
            }
        }
    }

    override fun onTorrentFinished(id: Int, hashString: String, name: String) {
        context!!.runOnUiThread {
            if (Settings.notifyOnFinished) {
                showFinishedNotification(id, hashString, name, this)
            }
        }
    }

    override fun onTorrentAddDuplicate() {
        context!!.runOnUiThread {
            torrentAddDuplicateListener?.invoke()
        }
    }

    override fun onTorrentAddError() {
        context!!.runOnUiThread {
            torrentAddErrorListener?.invoke()
        }
    }

    override fun onGotTorrentFiles(torrentId: Int) {
        context!!.runOnUiThread {
            gotTorrentFilesListener?.invoke(torrentId)
        }
    }

    override fun onTorrentFileRenamed(torrentId: Int, filePath: String, newName: String) {
        context!!.runOnUiThread {
            torrentFileRenamedListener?.invoke(torrentId, filePath, newName)
        }
    }

    override fun onGotTorrentPeers(torrentId: Int) {
        context!!.runOnUiThread {
            gotTorrentPeersListener?.invoke(torrentId)
        }
    }

    override fun onGotDownloadDirFreeSpace(bytes: Long) {
        context!!.runOnUiThread {
            gotDownloadDirFreeSpaceListener?.invoke(bytes)
        }
    }

    override fun onGotFreeSpaceForPath(path: String, success: Boolean, bytes: Long) {
        context!!.runOnUiThread {
            gotFreeSpaceForPathListener?.invoke(path, success, bytes)
        }
    }

    override fun onAboutToDisconnect() {
        context!!.runOnUiThread {
            if (disconnectingAfterCurrentServerChanged) {
                disconnectingAfterCurrentServerChanged = false
            } else {
                Servers.save()
            }
        }
    }

    private fun showTorrentNotification(torrentId: Int,
                                        hashString: String,
                                        name: String,
                                        notificationChannel: String,
                                        notificationTitle: String,
                                        context: Context) {
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(TorrentPropertiesActivity::class.java)
        stackBuilder.addNextIntent(context.intentFor<TorrentPropertiesActivity>(TorrentPropertiesActivity.HASH to hashString,
                TorrentPropertiesActivity.NAME to name))

        notificationManager?.notify(
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

    private fun showFinishedNotification(id: Int, hashString: String, name: String, context: Context) {
        showTorrentNotification(id,
                hashString,
                name,
                FINISHED_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.torrent_finished),
                context)
    }

    private fun showAddedNotification(id: Int, hashString: String, name: String, context: Context) {
        showTorrentNotification(id,
                hashString,
                name,
                ADDED_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.torrent_added),
                context)
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
            WorkManager.getInstance(context!!).enqueueUniquePeriodicWork(UpdateWorker.UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun cancelUpdateWorker() {
        info("Rpc.cancelUpdateWorker()")
        WorkManager.getInstance(context!!).cancelUniqueWork(UpdateWorker.UNIQUE_WORK_NAME)
    }

    private fun handleWorkerCompleter(connected: Boolean) {
        updateWorkerCompleter?.let { completer ->
            info("Rpc.handleWorkerCompleter()")
            if (connected) {
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
                instance.updateWorkerCompleter = completer
                if (instance.status() == Status.Disconnected) {
                    instance.connect()
                } else {
                    instance.updateData()
                }
                javaClass.simpleName
            }
        }
    }
}