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

package org.equeim.tremotesf.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.torrentfile.rpc.Rpc
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

        fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, ForegroundService::class.java).setAction(action)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    0,
                    intent,
                    flags
                )
            } else {
                PendingIntent.getService(
                    context,
                    0,
                    intent,
                    flags
                )
            }
        }

        private lateinit var startStopScope: CoroutineScope
        @MainThread
        fun startStopAutomatically() {
            if (::startStopScope.isInitialized) return
            startStopScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            combine(Settings.showPersistentNotification.flow(), AppForegroundTracker.hasStartedActivity, Boolean::and)
                .filter { it }
                .onEach { start(TremotesfApplication.instance) }
                .launchIn(startStopScope)
            Settings.showPersistentNotification.flow()
                .filter { !it }
                .onEach { stop(TremotesfApplication.instance) }
                .launchIn(startStopScope)
        }
    }

    private lateinit var rpc: Rpc
    private lateinit var notificationsController: NotificationsController

    var stopUpdatingNotification = false

    override fun onCreate() {
        super.onCreate()

        instances.add(this)
        AppForegroundTracker.registerForegroundService(this)

        Timber.i("onCreate: stopRequested = $stopRequested")

        rpc = GlobalRpc
        notificationsController = GlobalRpc.notificationsController

        combine(rpc.status, rpc.serverStats, GlobalServers.servers) { status, _, _ -> status }.drop(
            1
        ).launchAndCollectWhenStarted(this) { updatePersistentNotification(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GlobalRpc.wifiNetworkController.observingActiveWifiNetwork.launchAndCollectWhenStarted(
                this,
                ::startForegroundV29
            )
        } else {
            startForeground(
                NotificationsController.PERSISTENT_NOTIFICATION_ID,
                notificationsController.buildPersistentNotification(rpc)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startForegroundV29(observingWifiNetworks: Boolean) {
        Timber.i("startForeground() called with: observingWifiNetworks = $observingWifiNetworks")
        val type = if (observingWifiNetworks) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        startForeground(
            NotificationsController.PERSISTENT_NOTIFICATION_ID,
            notificationsController.buildPersistentNotification(rpc),
            type
        )
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
            NotificationsController.PersistentNotificationActions.CONNECT -> rpc.nativeInstance.connect()
            NotificationsController.PersistentNotificationActions.DISCONNECT -> rpc.nativeInstance.disconnect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("onDestroy: state = ${lifecycle.currentState}")
        stopForeground(true)
        instances.remove(this)
        super.onDestroy()
    }

    private fun stopSelfAndNotification() {
        stopUpdatingNotification = true
        stopSelf()
    }

    private fun updatePersistentNotification(status: Rpc.Status) {
        if (!stopUpdatingNotification) {
            notificationsController.updatePersistentNotification(rpc, status)
        }
    }
}
