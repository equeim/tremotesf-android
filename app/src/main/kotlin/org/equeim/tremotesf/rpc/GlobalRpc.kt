package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.libtremotesf.RpcError
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.service.NotificationsController
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object GlobalRpc : Rpc(GlobalServers, @OptIn(DelicateCoroutinesApi::class) GlobalScope, TremotesfApplication.instance) {
    private val context = TremotesfApplication.instance

    private val updateWorkerContinuation =
        AtomicReference<Continuation<Unit>>()

    val notificationsController = NotificationsController(context)

    init {
        connectionState
            .onEach { connectionState ->
                when (connectionState) {
                    RpcConnectionState.Connected -> {
                        withContext(dispatchers.Main) {
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
                    RpcConnectionState.Connecting -> {}
                }
            }
            .launchIn(scope + dispatchers.Unconfined)

        torrents
            .onEach {
                if (isConnected.value) {
                    handleWorkerCompleter()
                }
            }
            .launchIn(scope + dispatchers.Main)

        AppForegroundTracker.appInForeground
            .dropWhile { !it }
            .onEach(::onAppForegroundStateChanged)
            .launchIn(scope + dispatchers.Main)
    }

    override fun onTorrentFinished(id: Int, hashString: String, name: String) {
        scope.launch {
            notificationsController.showTorrentFinishedNotification(id, hashString, name, false)
        }
    }

    override fun onTorrentAdded(id: Int, hashString: String, name: String) {
        scope.launch {
            notificationsController.showTorrentAddedNotification(id, hashString, name, false)
        }
    }

    @MainThread
    private suspend fun onAppForegroundStateChanged(inForeground: Boolean) {
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
    private suspend fun enqueueUpdateWorker() {
        Timber.i("enqueueUpdateWorker() called")
        val interval = Settings.backgroundUpdateInterval.get()
        if (interval > 0 && notificationsController.isTorrentNotificationsEnabled(false)) {
            Timber.i("enqueueUpdateWorker: enqueueing worker, interval = $interval minutes")
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request =
                PeriodicWorkRequestBuilder<UpdateWorker>(interval, TimeUnit.MINUTES)
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
        updateWorkerContinuation.getAndSet(null)?.let { continuation ->
            Timber.i("handleWorkerCompleter: completing update worker")
            if (isConnected.value) {
                Timber.i("handleWorkerCompleter: save servers")
                withContext(dispatchers.Main) {
                    if (isConnected.value) {
                        servers.save()
                    }
                }
            }
            Timber.i("handleWorkerCompleter: setting worker result")
            continuation.resume(Unit)
        }
    }

    private suspend fun showNotificationsSinceLastConnection() {
        Timber.i("showNotificationsSinceLastConnection() called")
        val sinceLastConnection = updateWorkerContinuation.get() == null
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
        CoroutineWorker(context, workerParameters) {
        companion object {
            const val UNIQUE_WORK_NAME = "RpcUpdateWorker"
        }

        override suspend fun doWork(): Result {
            try {
                withContext(Dispatchers.Main) {
                    Timber.i("doWork() called")
                    if (AppForegroundTracker.appInForeground.value) {
                        Timber.w("startWork: app is in foreground, return")
                        return@withContext Result.success()
                    }

                    if (!GlobalServers.hasServers) {
                        Timber.w("startWork: no servers, return")
                        return@withContext Result.success()
                    }

                    if (!notificationsController.isTorrentNotificationsEnabled(false)) {
                        Timber.w("startWork:, notifications are disabled, return")
                        return@withContext Result.success()
                    }

                    suspendCancellableCoroutine<Unit> { continuation ->
                        val oldContinuation = updateWorkerContinuation.getAndSet(continuation)
                        oldContinuation?.resume(Unit)
                        continuation.invokeOnCancellation {
                            updateWorkerContinuation.compareAndSet(
                                continuation,
                                null
                            )
                        }

                        if (!wifiNetworkController.setCurrentServerFromWifiNetwork()) {
                            if (connectionState.value == RpcConnectionState.Disconnected) {
                                nativeInstance.connect()
                            } else {
                                nativeInstance.updateData()
                            }
                        }
                    }
                }
            } catch (ignore: CancellationException) {
                Timber.i("doWork: cancelled")
            }
            Timber.i("doWork() returned")
            return Result.success()
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
