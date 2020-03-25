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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.navigation.NavDeepLinkBuilder

import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils


private const val PERSISTENT_NOTIFICATION_ID = Int.MAX_VALUE
private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent"
private const val ACTION_CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
private const val ACTION_DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"
private const val ACTION_SHUTDOWN = "org.equeim.tremotesf.ACTION_SHUTDOWN"

class ForegroundService : LifecycleService(), Logger {
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService()!!
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info("ForegroundService.onStartCommand() intent=$intent, flags=$flags, startId=$startId, state=${lifecycle.currentState}")

        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_SHUTDOWN) {
            Utils.shutdownApp(this)
            return START_NOT_STICKY
        }

        if (lifecycle.currentState != Lifecycle.State.STARTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(NotificationChannel(PERSISTENT_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.persistent_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW))
            }

            startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())

            Rpc.status.observe(this, NotificationObserver())
            Rpc.error.observe(this, NotificationObserver())
            Rpc.serverStats.observe(this, NotificationObserver())
            Servers.currentServer.observe(this, NotificationObserver())

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
        info("ForegroundService.onDestroy()")
        // isPersistentNotificationActive() works only on API 23+, so
        // remove notification here explicitly to make sure that it is gone
        notificationManager.cancel(PERSISTENT_NOTIFICATION_ID)

        super.onDestroy()
    }

    private fun isPersistentNotificationActive(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return notificationManager.activeNotifications.any { it.id == PERSISTENT_NOTIFICATION_ID }
        }
        return true
    }

    private fun updatePersistentNotification() {
        // Sometimes updatePersistentNotification() is called after system has already removed notification
        // but ForegroundService.onDestroy() hasn't been called yet. Check isPersistentNotificationActive()
        // to avoid creating a new notification
        if (isPersistentNotificationActive()) {
            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
        }
    }

    private fun buildPersistentNotification(): Notification {
        val notificationBuilder = NotificationCompat.Builder(applicationContext, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(NavDeepLinkBuilder(applicationContext)
                                          .setGraph(R.navigation.nav_main)
                                          .setDestination(R.id.torrentsListFragment)
                                          .createPendingIntent())
                .setColor(ResourcesCompat.getColor(resources, android.R.color.white, null))
                .setOngoing(true)

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

        if (Rpc.isConnected) {
            val stats = Rpc.serverStats.value
            notificationBuilder.setContentText(getString(R.string.main_activity_subtitle,
                                                         Utils.formatByteSpeed(this,
                                                                               stats.downloadSpeed),
                                                         Utils.formatByteSpeed(this,
                                                                               stats.uploadSpeed)))
        } else {
            notificationBuilder.setContentText(Rpc.statusString)
        }

        if (Rpc.status.value == RpcStatus.Disconnected) {
            notificationBuilder.addAction(
                    R.drawable.notification_connect,
                    getString(R.string.connect),
                    PendingIntent.getService(this,
                                             0,
                                             Intent(this, javaClass).setAction(ACTION_CONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            notificationBuilder.addAction(
                    R.drawable.notification_disconnect,
                    getString(R.string.disconnect),
                    PendingIntent.getService(this,
                                             0,
                                             Intent(this, javaClass).setAction(ACTION_DISCONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        }

        notificationBuilder.addAction(
                R.drawable.notification_quit,
                getString(R.string.quit),
                PendingIntent.getService(this,
                                         0,
                                         Intent(this, javaClass).setAction(ACTION_SHUTDOWN),
                                         PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return notificationBuilder.build()
    }

    private inner class NotificationObserver<T> : Observer<T> {
        private var gotFirstValue = false
        override fun onChanged(t: T) {
            if (gotFirstValue) {
                updatePersistentNotification()
            } else {
                gotFirstValue = true
            }
        }
    }
}