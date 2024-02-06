// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.MainThread
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.PeriodicServerStateUpdater
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.ui.AppForegroundTracker
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import timber.log.Timber

class ForegroundService : LifecycleService() {
    companion object {
        private var startRequestInProgress = false
        private var stopRequested = false

        private val instances = mutableListOf<ForegroundService>()

        @MainThread
        fun start(context: Context) {
            Timber.i("start()")
            ContextCompat.startForegroundService(
                context,
                Intent(context, ForegroundService::class.java)
            )
            startRequestInProgress = true
        }

        @MainThread
        fun stop(context: Context) {
            Timber.i("stop()")
            if (startRequestInProgress) {
                Timber.w("onStartCommand() haven't been called yet, set stopRequested=true")
                stopRequested = true
            } else {
                instances.forEach { it.stopUpdatingNotification = true }
                context.stopService(Intent(context, ForegroundService::class.java))
            }
        }

        fun getPendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, ForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private lateinit var startStopScope: CoroutineScope

        @MainThread
        fun startStopAutomatically() {
            if (::startStopScope.isInitialized) return
            startStopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            combine(
                Settings.showPersistentNotification.flow(),
                AppForegroundTracker.hasStartedActivity,
                Boolean::and
            )
                .filter { it }
                .onEach { start(TremotesfApplication.instance) }
                .launchIn(startStopScope)
            Settings.showPersistentNotification.flow()
                .filter { !it }
                .onEach { stop(TremotesfApplication.instance) }
                .launchIn(startStopScope)
        }
    }

    private var stopUpdatingNotification = false

    override fun onCreate() {
        super.onCreate()

        instances.add(this)
        AppForegroundTracker.registerForegroundService(this)

        Timber.i("onCreate: stopRequested = $stopRequested")

        combine(
            GlobalServers.currentServer,
            PeriodicServerStateUpdater.sessionStats,
            ::Pair
        )
            .drop(1)
            .launchAndCollectWhenStarted(this) { (currentServer, serverStats) ->
                updatePersistentNotification(currentServer, serverStats)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GlobalServers.wifiNetworkController.observingActiveWifiNetwork.launchAndCollectWhenStarted(
                this,
                ::startForeground
            )
        } else {
            startForeground(observingWifiNetworks = false)
        }
    }

    private fun startForeground(observingWifiNetworks: Boolean) {
        Timber.i("startForeground() called with: observingWifiNetworks = $observingWifiNetworks")
        val notification = PeriodicServerStateUpdater.notificationsController.buildPersistentNotification(
            GlobalServers.serversState.value.currentServer,
            PeriodicServerStateUpdater.sessionStats.value
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (observingWifiNetworks) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NotificationsController.PERSISTENT_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NotificationsController.PERSISTENT_NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i(
            "onStartCommand() called with: intent = $intent, flags = $flags, startId = $startId"
        )
        Timber.i("onStartCommand: state = ${lifecycle.currentState}, stopRequested = $stopRequested")

        super.onStartCommand(intent, flags, startId)

        startRequestInProgress = false

        if (stopRequested) {
            Timber.w("onStartCommand: ForegroundService.stop() was called before onStartCommand(), stop now")
            stopRequested = false
            stopSelfAndNotification()
            return START_NOT_STICKY
        }

        if (intent?.action == NotificationsController.PersistentNotificationActions.SHUTDOWN_APP) {
            stopSelfAndNotification()
            Utils.shutdownApp(this, false)
            return START_NOT_STICKY
        }

        if (lifecycle.currentState == Lifecycle.State.CREATED) {
            if (intent?.action != null) {
                Timber.w("onStartCommand() is called for the first time, but intent action is not null, stop now")
                stopSelfAndNotification()
                return START_NOT_STICKY
            }

            Timber.i("onStartCommand: service started")
        }

        when (intent?.action) {
            NotificationsController.PersistentNotificationActions.CONNECT -> GlobalRpcClient.shouldConnectToServer.value =
                true

            NotificationsController.PersistentNotificationActions.DISCONNECT -> GlobalRpcClient.shouldConnectToServer.value =
                false

            NotificationsController.PersistentNotificationActions.STOP_UPDATING_NOTIFICATION -> stopUpdatingNotification = true
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("onDestroy: state = ${lifecycle.currentState}")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        instances.remove(this)
        super.onDestroy()
    }

    private fun stopSelfAndNotification() {
        stopUpdatingNotification = true
        stopSelf()
    }

    private fun updatePersistentNotification(
        currentServer: Server?,
        sessionStats: RpcRequestState<SessionStatsResponseArguments>,
    ) {
        if (!stopUpdatingNotification) {
            PeriodicServerStateUpdater.notificationsController.updatePersistentNotification(currentServer, sessionStats)
        } else {
            Timber.i("Not updating persistent notification")
        }
    }
}
