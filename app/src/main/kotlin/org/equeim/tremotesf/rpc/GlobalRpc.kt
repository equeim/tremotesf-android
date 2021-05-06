package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.concurrent.futures.CallbackToFutureAdapter
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
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.Application
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.data.rpc.RpcError
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.service.NotificationsController
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.utils.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("StaticFieldLeak")
object GlobalRpc : Rpc(GlobalServers) {
    private val context = Application.instance

    private val updateWorkerCompleter =
        AtomicReference<CallbackToFutureAdapter.Completer<ListenableWorker.Result>>()

    private val notificationsController = NotificationsController(context)

    init {
        connectionState
            .onEach { connectionState ->
                when (connectionState) {
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
            .launchIn(GlobalScope + Dispatchers.Unconfined)

        torrents
            .onEach {
                if (isConnected.value) {
                    handleWorkerCompleter()
                }
            }
            .launchIn(GlobalScope + Dispatchers.Main)

        AppForegroundTracker.appInForeground
            .dropWhile { !it }
            .onEach(::onAppForegroundStateChanged)
            .launchIn(GlobalScope + Dispatchers.Main)

        servers.setRpc(this)
    }

    override fun onTorrentFinished(id: Int, hashString: String, name: String) {
        notificationsController.showTorrentFinishedNotification(id, hashString, name, false)
    }

    override fun onTorrentAdded(id: Int, hashString: String, name: String) {
        notificationsController.showTorrentAddedNotification(id, hashString, name, false)
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

    @MainThread
    private fun enqueueUpdateWorker() {
        info("enqueueUpdateWorker() called")
        val interval = Settings.backgroundUpdateInterval
        if (interval > 0 && notificationsController.isTorrentNotificationsEnabled(false)) {
            info("enqueueUpdateWorker: enqueueing worker, interval = $interval minutes")
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request =
                PeriodicWorkRequest.Builder(UpdateWorker::class.java, interval, TimeUnit.MINUTES)
                    .setInitialDelay(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UpdateWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            ).state.observeForever { state ->
                if (state !is Operation.State.IN_PROGRESS) {
                    info("enqueueUpdateWorker: enqueuing worker result = $state")
                }
            }
        } else {
            info("enqueueUpdateWorker: not enqueueing worker, disabled in settings")
        }
    }

    @MainThread
    private fun cancelUpdateWorker() {
        info("cancelUpdateWorker() called")
        WorkManager.getInstance(context)
            .cancelUniqueWork(UpdateWorker.UNIQUE_WORK_NAME).state.observeForever { state ->
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
                        servers.save()
                    }
                }
            }
            info("handleWorkerCompleter: setting worker result")
            completer.set(ListenableWorker.Result.success())
        }
    }

    @MainThread
    private fun showNotificationsSinceLastConnection() {
        info("showNotificationsSinceLastConnection() called")
        val sinceLastConnection = updateWorkerCompleter.get() == null
        val notifyOnFinished = notificationsController.isNotifyOnFinishedEnabled(sinceLastConnection)
        val notifyOnAdded = notificationsController.isNotifyOnFinishedEnabled(sinceLastConnection)

        if (!notifyOnFinished && !notifyOnAdded) {
            info("showNotificationsSinceLastConnection: notifications are disabled")
            return
        }

        val server = servers.currentServer.value
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
                        notificationsController.showTorrentAddedNotification(
                            torrent.id,
                            hashString,
                            torrent.name,
                            sinceLastConnection
                        )
                    }
                } else {
                    if (!oldTorrent.finished && (torrent.isFinished) && notifyOnFinished) {
                        notificationsController.showTorrentFinishedNotification(
                            torrent.id,
                            hashString,
                            torrent.name,
                            sinceLastConnection
                        )
                    }
                }
            }
        }
    }

    class UpdateWorker(context: Context, workerParameters: WorkerParameters) :
        ListenableWorker(context, workerParameters), Logger {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override fun startWork(): ListenableFuture<Result> {
            info("startWork() called")

            if (AppForegroundTracker.appInForeground.value) {
                warn("startWork: app is in foreground, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!GlobalServers.hasServers) {
                warn("startWork: no servers, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!notificationsController.isTorrentNotificationsEnabled(false)) {
                warn("startWork:, notifications are disabled, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            return CallbackToFutureAdapter.getFuture { completer ->
                updateWorkerCompleter.getAndSet(completer)?.set(Result.success())
                if (!GlobalServers.setCurrentServerFromWifiNetwork()) {
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

val Rpc.Status.statusString: String
    get() = when (connectionState) {
        RpcConnectionState.Disconnected -> when (error.error) {
            RpcError.NoError -> Application.instance.getString(R.string.disconnected)
            RpcError.TimedOut -> Application.instance.getString(R.string.timed_out)
            RpcError.ConnectionError -> Application.instance.getString(R.string.connection_error)
            RpcError.AuthenticationError -> Application.instance.getString(R.string.authentication_error)
            RpcError.ParseError -> Application.instance.getString(R.string.parsing_error)
            RpcError.ServerIsTooNew -> Application.instance.getString(R.string.server_is_too_new)
            RpcError.ServerIsTooOld -> Application.instance.getString(R.string.server_is_too_old)
            else -> Application.instance.getString(R.string.disconnected)
        }
        RpcConnectionState.Connecting -> Application.instance.getString(R.string.connecting)
        RpcConnectionState.Connected -> Application.instance.getString(R.string.connected)
        else -> Application.instance.getString(R.string.disconnected)
    }

val Torrent.statusString: String
    get() = when (status) {
        TorrentData.Status.Paused -> Application.instance.getString(R.string.torrent_paused)
        TorrentData.Status.Downloading -> Application.instance.resources.getQuantityString(
            R.plurals.torrent_downloading,
            seeders,
            seeders
        )
        TorrentData.Status.StalledDownloading -> Application.instance.getString(R.string.torrent_downloading_stalled)
        TorrentData.Status.Seeding -> Application.instance.resources.getQuantityString(
            R.plurals.torrent_seeding,
            leechers,
            leechers
        )
        TorrentData.Status.StalledSeeding -> Application.instance.getString(R.string.torrent_seeding_stalled)
        TorrentData.Status.QueuedForDownloading,
        TorrentData.Status.QueuedForSeeding -> Application.instance.getString(R.string.torrent_queued)
        TorrentData.Status.Checking -> Application.instance.getString(
            R.string.torrent_checking,
            DecimalFormats.generic.format(recheckProgress * 100)
        )
        TorrentData.Status.QueuedForChecking -> Application.instance.getString(R.string.torrent_queued_for_checking)
        TorrentData.Status.Errored -> errorString
        else -> ""
    }
