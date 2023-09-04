// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.Operation.State.FAILURE
import androidx.work.Operation.State.SUCCESS
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.service.NotificationsController
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcTorrentFinishedState
import org.equeim.tremotesf.torrentfile.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.torrentfile.rpc.requests.getSessionStats
import org.equeim.tremotesf.torrentfile.rpc.requests.getTorrentsFinishedState
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object PeriodicServerStateUpdater {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = TremotesfApplication.instance
    private val workManager = WorkManager.getInstance(context)

    val notificationsController = NotificationsController(context)

    val sessionStateRefreshRequests = MutableSharedFlow<Unit>()
    val sessionStats: StateFlow<RpcRequestState<SessionStatsResponseArguments>> =
        GlobalRpcClient.performPeriodicRequest(sessionStateRefreshRequests) { getSessionStats() }
            .stateIn(GlobalRpcClient, coroutineScope)

    val showingTorrentsListScreen = MutableStateFlow(false)

    private val updatedTorrentsSinceEnablingConnection = AtomicBoolean(false)
    private val onTorrentsUpdatedMutex = Mutex()

    init {
        coroutineScope.launch {
            AppForegroundTracker.hasStartedActivity.collectLatest {
                if (it) {
                    showingTorrentsListScreen.collectLatest { showingTorrentsListScreen ->
                        if (showingTorrentsListScreen) {
                            Timber.d("Showing torrents list screen, not performing torrents finished state requests")
                        } else {
                            Timber.d("Not showing torrents list screen, performing torrents finished state requests")
                            GlobalRpcClient.performPeriodicRequest { getTorrentsFinishedState() }
                                .mapNotNull { (it as? RpcRequestState.Loaded)?.response }
                                .collect(::onTorrentsUpdated)
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            GlobalRpcClient.shouldConnectToServer.collect {
                if (!it) {
                    updatedTorrentsSinceEnablingConnection.set(false)
                }
            }
        }

        coroutineScope.launch {
            AppForegroundTracker.appInForeground.collect { inForeground ->
                if (inForeground) {
                    Timber.d("Cancelling background notifications job")
                    workManager.cancelUniqueWork(BackgroundNotificationsWorker::class.qualifiedName!!)
                        .observe().collect {
                            Timber.d("Cancelling background notifications worker: $it")
                        }
                } else {
                    val interval = Settings.backgroundUpdateInterval.get()
                    if (interval > 0 && notificationsController.isTorrentNotificationsEnabled(false)) {
                        val constraints =
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                        val request =
                            PeriodicWorkRequestBuilder<BackgroundNotificationsWorker>(interval, TimeUnit.MINUTES)
                                .setInitialDelay(interval, TimeUnit.MINUTES)
                                .setConstraints(constraints)
                                .build()
                        Timber.d("Scheduling background notifications job, interval = $interval minutes")
                        workManager.enqueueUniquePeriodicWork(
                            BackgroundNotificationsWorker::class.qualifiedName!!,
                            ExistingPeriodicWorkPolicy.KEEP,
                            request
                        ).observe().collect {
                            Timber.d("Scheduling background notifications job: $it")
                        }
                    } else {
                        Timber.i("Not scheduling job, disabled in settings")
                    }
                }
            }
        }
    }

    private suspend fun onTorrentsUpdated(torrents: List<RpcTorrentFinishedState>) =
        onTorrentsUpdatedMutex.withLock {
            Timber.d("Updating finished state for ${torrents.size} torrents")
            val firstUpdate = updatedTorrentsSinceEnablingConnection.compareAndSet(false, true)
            val oldFinishedState = GlobalServers.serversState.value.currentServer?.lastTorrentsFinishedState
            val newFinishedState = torrents.associateBy(
                keySelector = { Server.TorrentHashString(it.hashString) },
                valueTransform = { Server.TorrentFinishedState(it.isFinished, it.sizeWhenDone) }
            )
            if (newFinishedState == oldFinishedState) {
                Timber.d("Torrents finished state has not changed")
                return
            }
            if (oldFinishedState != null) {
                val notifyOnFinished =
                    notificationsController.isNotifyOnFinishedEnabled(sinceLastConnection = firstUpdate)
                val notifyOnAdded = notificationsController.isNotifyOnAddedEnabled(sinceLastConnection = firstUpdate)
                if (notifyOnFinished || notifyOnAdded) {
                    for (newState in torrents) {
                        val oldState = oldFinishedState[Server.TorrentHashString(newState.hashString)]
                        if (oldState != null) {
                            if (notifyOnFinished && shouldNotifyFinished(oldState, newState)) {
                                notificationsController.showTorrentFinishedNotification(
                                    newState.hashString,
                                    newState.name
                                )
                            }
                        } else {
                            if (notifyOnAdded) {
                                notificationsController.showTorrentAddedNotification(newState.hashString, newState.name)
                            }
                        }
                    }
                }
            }
            GlobalServers.saveCurrentServerTorrentsFinishedState(newFinishedState)
        }

    suspend fun updateTorrentsFromBackground(): Boolean {
        val torrentsFinishedState = try {
            GlobalRpcClient.getTorrentsFinishedState()
        } catch (e: RpcRequestError) {
            return false
        }
        onTorrentsUpdated(torrentsFinishedState)
        return true
    }
}

private fun shouldNotifyFinished(
    oldFinishedState: Server.TorrentFinishedState,
    newFinishedState: RpcTorrentFinishedState,
): Boolean {
    return !oldFinishedState.isFinished &&
            newFinishedState.isFinished &&
            // Don't show notification when incomplete files are deselected
            newFinishedState.sizeWhenDone.bytes >= oldFinishedState.sizeWhenDone.bytes
}

class BackgroundNotificationsWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        Timber.d("doWork() called")
        if (AppForegroundTracker.appInForeground.value) {
            Timber.w("App is in foreground, return")
            return Result.success()
        }
        if (GlobalServers.serversState.value.servers.isEmpty()) {
            Timber.w("No servers, return")
            return Result.success()
        }
        GlobalServers.wifiNetworkController.setCurrentServerFromWifiNetwork()
        return if (PeriodicServerStateUpdater.updateTorrentsFromBackground()) {
            Result.success()
        } else {
            Result.failure()
        }.also {
            Timber.i("doWork() returned $it")
        }
    }
}

private fun Operation.observe(): Flow<Operation.State> = state.asFlow().takeWhile {
    when (it) {
        is SUCCESS, is FAILURE -> false
        else -> true
    }
}
