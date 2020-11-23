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

package org.equeim.tremotesf.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.navigation.NavDeepLinkBuilder

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop

import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.data.rpc.Servers
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.collectWhenStarted


private const val PERSISTENT_NOTIFICATION_ID = Int.MAX_VALUE
private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent"
private const val ACTION_CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
private const val ACTION_DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"
private const val ACTION_SHUTDOWN_APP = "org.equeim.tremotesf.ACTION_SHUTDOWN_APP"


class ForegroundService : LifecycleService(), Logger {
    companion object : Logger {
        private var startRequestInProgress = false
        private var stopRequested = false

        private val instances = mutableListOf<ForegroundService>()

        fun start(context: Context) {
            info("start()")
            ContextCompat.startForegroundService(context, Intent(context, ForegroundService::class.java))
            startRequestInProgress = true
        }

        fun stop(context: Context) {
            info("stop()")
            if (startRequestInProgress) {
                warn("onStartCommand() haven't been called yet, set stopRequested=true")
                stopRequested = true
            } else {
                instances.forEach { it.stopUpdatingNotification = true }
                context.stopService(Intent(context, ForegroundService::class.java))
            }
        }
    }

    private lateinit var notificationManager: NotificationManager

    var stopUpdatingNotification = false

    override fun onCreate() {
        super.onCreate()

        instances.add(this)

        info("onCreate() stopRequested=$stopRequested")

        notificationManager = getSystemService()!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(PERSISTENT_NOTIFICATION_CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(NotificationChannel(PERSISTENT_NOTIFICATION_CHANNEL_ID,
                                                                                  getString(R.string.persistent_notification_channel_name),
                                                                                  NotificationManager.IMPORTANCE_LOW))
            }
        }

        startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification(Rpc.status.value))

        combine(Rpc.status, Rpc.serverStats, Servers.currentServer) { status, _, _ -> status }.drop(1)
                .collectWhenStarted(this) { updatePersistentNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info("onStartCommand() intent=$intent, flags=$flags, startId=$startId, state=${lifecycle.currentState}, stopRequested=$stopRequested")

        super.onStartCommand(intent, flags, startId)

        startRequestInProgress = false

        if (stopRequested) {
            warn("ForegroundService.stop() was called before onStartCommand(), stop now")
            stopRequested = false
            stopSelfAndNotification()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SHUTDOWN_APP) {
            stopSelfAndNotification()
            Utils.shutdownApp(this, false)
            return START_NOT_STICKY
        }

        if (lifecycle.currentState == Lifecycle.State.CREATED) {
            if (intent?.action != null) {
                warn("onStartCommand() is called for the first time, but intent action is not null, stop now")
                stopSelfAndNotification()
                return START_NOT_STICKY
            }

            Rpc.connectOnce()

            info("Service started")
        }

        when (intent?.action) {
            ACTION_CONNECT -> Rpc.nativeInstance.connect()
            ACTION_DISCONNECT -> Rpc.nativeInstance.disconnect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        info("onDestroy() ${lifecycle.currentState}")
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
            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification(status))
        }
    }

    private fun buildPersistentNotification(status: Rpc.Status): Notification {
        val notificationBuilder = NotificationCompat.Builder(applicationContext, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(NavDeepLinkBuilder(applicationContext)
                                          .setGraph(R.navigation.nav_main)
                                          .setDestination(R.id.torrentsListFragment)
                                          .createPendingIntent())

        val currentServer = Servers.currentServer.value
        if (currentServer != null) {
            notificationBuilder.setContentTitle(getString(R.string.current_server_string,
                                                          currentServer.name,
                                                          currentServer.address))
        } else {
            notificationBuilder.setContentTitle(getString(R.string.no_servers))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            notificationBuilder.setWhen(0)
        } else {
            notificationBuilder.setShowWhen(false)
        }

        if (status.isConnected) {
            val stats = Rpc.serverStats.value
            notificationBuilder.setContentText(getString(R.string.main_activity_subtitle,
                                                         Utils.formatByteSpeed(this,
                                                                               stats.downloadSpeed),
                                                         Utils.formatByteSpeed(this,
                                                                               stats.uploadSpeed)))
        } else {
            notificationBuilder.setContentText(status.statusString)
        }

        if (status.connectionState == RpcConnectionState.Disconnected) {
            notificationBuilder.addAction(
                    R.drawable.notification_connect,
                    getString(R.string.connect),
                    getPendingIntent(ACTION_CONNECT))
        } else {
            notificationBuilder.addAction(
                    R.drawable.notification_disconnect,
                    getString(R.string.disconnect),
                    getPendingIntent(ACTION_DISCONNECT))
        }

        notificationBuilder.addAction(
                R.drawable.notification_quit,
                getString(R.string.quit),
                getPendingIntent(ACTION_SHUTDOWN_APP)
        )

        return notificationBuilder.build()
    }

    private fun getPendingIntent(action: String): PendingIntent? {
        val intent = Intent(this, ForegroundService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this,
                                               0,
                                               intent,
                                               PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(this,
                                     0,
                                     intent,
                                     PendingIntent.FLAG_UPDATE_CURRENT)
        }

    }
}