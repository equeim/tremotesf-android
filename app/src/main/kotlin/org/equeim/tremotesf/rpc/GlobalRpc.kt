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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.data.rpc.RpcError
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.service.NotificationsController
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.DecimalFormats
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("StaticFieldLeak")
object GlobalRpc : Rpc(GlobalServers, @OptIn(DelicateCoroutinesApi::class) GlobalScope, TremotesfApplication.instance) {
    private val context = TremotesfApplication.instance

    private val updateWorkerCompleter =
        AtomicReference<CallbackToFutureAdapter.Completer<ListenableWorker.Result>>()

    val notificationsController = NotificationsController(context)

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
                            Timber.i("Disconnected after current server changed")
                        } else {
                            handleWorkerCompleter()
                        }
                    }
                }
            }
            .launchIn(scope + Dispatchers.Unconfined)

        torrents
            .onEach {
                if (isConnected.value) {
                    handleWorkerCompleter()
                }
            }
            .launchIn(scope + Dispatchers.Main)

        AppForegroundTracker.appInForeground
            .dropWhile { !it }
            .onEach(::onAppForegroundStateChanged)
            .launchIn(scope + Dispatchers.Main)
    }

    override fun onTorrentFinished(id: Int, hashString: String, name: String) {
        notificationsController.showTorrentFinishedNotification(id, hashString, name, false)
    }

    override fun onTorrentAdded(id: Int, hashString: String, name: String) {
        notificationsController.showTorrentAddedNotification(id, hashString, name, false)
    }

    @MainThread
    private fun onAppForegroundStateChanged(inForeground: Boolean) {
        Timber.i("onAppForegroundStateChanged() called with: inForeground = $inForeground")
        if (inForeground) {
            connectOnce()
            cancelUpdateWorker()
        } else {
            enqueueUpdateWorker()
        }
        wifiNetworkController.enabled.value = inForeground
        nativeInstance.setUpdateDisabled(!inForeground)
    }

    @MainThread
    private fun enqueueUpdateWorker() {
        Timber.i("enqueueUpdateWorker() called")
        val interval = Settings.backgroundUpdateInterval
        if (interval > 0 && notificationsController.isTorrentNotificationsEnabled(false)) {
            Timber.i("enqueueUpdateWorker: enqueueing worker, interval = $interval minutes")
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
                    Timber.i("enqueueUpdateWorker: enqueuing worker result = $state")
                }
            }
        } else {
            Timber.i("enqueueUpdateWorker: not enqueueing worker, disabled in settings")
        }
    }

    @MainThread
    private fun cancelUpdateWorker() {
        Timber.i("cancelUpdateWorker() called")
        WorkManager.getInstance(context)
            .cancelUniqueWork(UpdateWorker.UNIQUE_WORK_NAME).state.observeForever { state ->
                if (state !is Operation.State.IN_PROGRESS) {
                    Timber.i("cancelUpdateWorker: cancelling worker result = $state")
                }
            }
    }

    @AnyThread
    private suspend fun handleWorkerCompleter() {
        updateWorkerCompleter.getAndSet(null)?.let { completer ->
            Timber.i("handleWorkerCompleter: completing update worker")
            if (isConnected.value) {
                Timber.i("handleWorkerCompleter: save servers")
                withContext(Dispatchers.Main) {
                    if (isConnected.value) {
                        servers.save()
                    }
                }
            }
            Timber.i("handleWorkerCompleter: setting worker result")
            completer.set(ListenableWorker.Result.success())
        }
    }

    @MainThread
    private fun showNotificationsSinceLastConnection() {
        Timber.i("showNotificationsSinceLastConnection() called")
        val sinceLastConnection = updateWorkerCompleter.get() == null
        val notifyOnFinished = notificationsController.isNotifyOnFinishedEnabled(sinceLastConnection)
        val notifyOnAdded = notificationsController.isNotifyOnFinishedEnabled(sinceLastConnection)

        if (!notifyOnFinished && !notifyOnAdded) {
            Timber.i("showNotificationsSinceLastConnection: notifications are disabled")
            return
        }

        val server = servers.currentServer.value
        if (server == null) {
            Timber.e("showNotificationsSinceLastConnection: server is null")
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
        ListenableWorker(context, workerParameters) {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override fun startWork(): ListenableFuture<Result> {
            Timber.i("startWork() called")

            if (AppForegroundTracker.appInForeground.value) {
                Timber.w("startWork: app is in foreground, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!GlobalServers.hasServers) {
                Timber.w("startWork: no servers, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            if (!notificationsController.isTorrentNotificationsEnabled(false)) {
                Timber.w("startWork:, notifications are disabled, return")
                return CallbackToFutureAdapter.getFuture { it.set(Result.success()) }
            }

            return CallbackToFutureAdapter.getFuture { completer ->
                updateWorkerCompleter.getAndSet(completer)?.set(Result.success())
                if (!wifiNetworkController.setCurrentServerFromWifiNetwork()) {
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
            Timber.i("onStopped() called")
        }
    }
}

val Rpc.Status.statusString: String
    get() {
        val context = TremotesfApplication.instance
        return when (connectionState) {
            RpcConnectionState.Disconnected -> when (error.error) {
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

val Torrent.statusString: String
    get() {
        val context = TremotesfApplication.instance
        return when (status) {
            TorrentData.Status.Paused -> context.getString(R.string.torrent_paused)
            TorrentData.Status.Downloading -> context.resources.getQuantityString(
                R.plurals.torrent_downloading,
                seeders,
                seeders
            )
            TorrentData.Status.StalledDownloading -> context.getString(R.string.torrent_downloading_stalled)
            TorrentData.Status.Seeding -> context.resources.getQuantityString(
                R.plurals.torrent_seeding,
                leechers,
                leechers
            )
            TorrentData.Status.StalledSeeding -> context.getString(R.string.torrent_seeding_stalled)
            TorrentData.Status.QueuedForDownloading,
            TorrentData.Status.QueuedForSeeding -> context.getString(R.string.torrent_queued)
            TorrentData.Status.Checking -> context.getString(
                R.string.torrent_checking,
                DecimalFormats.generic.format(recheckProgress * 100)
            )
            TorrentData.Status.QueuedForChecking -> context.getString(R.string.torrent_queued_for_checking)
            TorrentData.Status.Errored -> errorString
            else -> ""
        }
    }
