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
import android.app.Service
import android.content.Intent
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.intentFor

import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.utils.Utils


private const val PERSISTENT_NOTIFICATION_ID = Int.MAX_VALUE
private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent"
private const val ACTION_CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
private const val ACTION_DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"

class ForegroundService : Service(), AnkoLogger {
    companion object {
        var instance: ForegroundService? = null
            private set


    }

    private lateinit var notificationManager: NotificationManager

    private val rpcStatusListener: (Int) -> Unit = { updatePersistentNotification() }
    private val serverStatsUpdatedListener = { updatePersistentNotification() }
    private val rpcErrorListener: (Int) -> Unit = { updatePersistentNotification() }
    private val currentServerListener = { updatePersistentNotification() }

    override fun onBind(intent: Intent) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_SHUTDOWN) {
            Utils.shutdownApp(this)
            return START_NOT_STICKY
        }

        if (instance == this) {
            when (intent?.action) {
                ACTION_CONNECT -> Rpc.nativeInstance.connect()
                ACTION_DISCONNECT -> Rpc.nativeInstance.disconnect()
            }
        } else {
            Utils.initApp(applicationContext)

            notificationManager = getSystemService()!!

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(NotificationChannel(PERSISTENT_NOTIFICATION_CHANNEL_ID,
                                                                                  getString(R.string.persistent_notification_channel_name),
                                                                                  NotificationManager.IMPORTANCE_LOW))
            }

            Rpc.addStatusListener(rpcStatusListener)
            Rpc.addErrorListener(rpcErrorListener)
            Rpc.addServerStatsUpdatedListener(serverStatsUpdatedListener)

            Servers.addCurrentServerListener(currentServerListener)

            startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())

            instance = this

            debug("service started")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        debug("service destroyed")
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeErrorListener(rpcErrorListener)
        Rpc.removeServerStatsUpdatedListener(serverStatsUpdatedListener)
        instance = null
    }

    private fun updatePersistentNotification() {
        notificationManager.notify(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
    }

    private fun buildPersistentNotification(): Notification {
        val notificationBuilder = NotificationCompat.Builder(applicationContext, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        intentFor<MainActivity>(),
                        PendingIntent.FLAG_UPDATE_CURRENT
                ))
                .setColor(ResourcesCompat.getColor(resources, android.R.color.white, null))
                .setOngoing(true)

        if (Servers.hasServers) {
            val currentServer = Servers.currentServer!!
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
            notificationBuilder.setContentText(getString(R.string.main_activity_subtitle,
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.serverStats.downloadSpeed()),
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.serverStats.uploadSpeed())))
        } else {
            notificationBuilder.setContentText(Rpc.statusString)
        }

        if (Rpc.status == RpcStatus.Disconnected) {
            notificationBuilder.addAction(
                    R.drawable.notification_connect,
                    getString(R.string.connect),
                    PendingIntent.getService(this,
                                             0,
                                             intentFor<ForegroundService>().setAction(ACTION_CONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            notificationBuilder.addAction(
                    R.drawable.notification_disconnect,
                    getString(R.string.disconnect),
                    PendingIntent.getService(this,
                                             0,
                                             intentFor<ForegroundService>().setAction(ACTION_DISCONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        }

        notificationBuilder.addAction(
                R.drawable.notification_quit,
                getString(R.string.quit),
                PendingIntent.getService(this,
                                         0,
                                         intentFor<ForegroundService>().setAction(Intent.ACTION_SHUTDOWN),
                                         PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return notificationBuilder.build()
    }
}