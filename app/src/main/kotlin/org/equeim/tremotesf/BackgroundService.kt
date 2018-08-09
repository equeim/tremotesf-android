/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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
import android.content.Context

import android.content.Intent

import android.os.Build

import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.support.v4.content.res.ResourcesCompat

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.intentFor

import org.equeim.libtremotesf.BaseRpc
import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity
import org.equeim.tremotesf.utils.Utils


private const val PERSISTENT_NOTIFICATION_ID = Int.MAX_VALUE
private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent"
private const val ACTION_CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
private const val ACTION_DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"
private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

class BackgroundService : Service(), AnkoLogger {
    companion object {
        var instance: BackgroundService? = null
            private set

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

            (context.applicationContext as Application).notificationManager.notify(
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

        fun showFinishedNotification(id: Int, hashString: String, name: String, context: Context) {
            showTorrentNotification(id,
                                    hashString,
                                    name,
                                    FINISHED_NOTIFICATION_CHANNEL_ID,
                                    context.getString(R.string.torrent_finished),
                                    context)
        }

        fun showAddedNotification(id: Int, hashString: String, name: String, context: Context) {
            showTorrentNotification(id,
                                    hashString,
                                    name,
                                    ADDED_NOTIFICATION_CHANNEL_ID,
                                    context.getString(R.string.torrent_added),
                                    context)
        }
    }

    private lateinit var notificationManager: NotificationManager

    private val rpcStatusListener = { _: Int -> updatePersistentNotification() }
    private val serverStatsUpdatedListener = { updatePersistentNotification() }
    private val rpcErrorListener = { _: Int -> updatePersistentNotification() }
    private val currentServerListener = { updatePersistentNotification() }

    override fun onBind(intent: Intent) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_SHUTDOWN) {
            Utils.shutdownApp()
            return START_NOT_STICKY
        }

        if (instance == this) {
            when (intent?.action) {
                ACTION_CONNECT -> Rpc.instance.connect()
                ACTION_DISCONNECT -> Rpc.instance.disconnect()
            }
        } else {
            debug("service started")

            Utils.initApp(applicationContext)

            notificationManager = (application as Application).notificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannels(listOf(NotificationChannel(PERSISTENT_NOTIFICATION_CHANNEL_ID,
                                                                                          getString(R.string.persistent_notification_channel_name),
                                                                                          NotificationManager.IMPORTANCE_LOW),
                                                                      NotificationChannel(FINISHED_NOTIFICATION_CHANNEL_ID,
                                                                                          getString(R.string.finished_torrents_channel_name),
                                                                                          NotificationManager.IMPORTANCE_DEFAULT),
                                                                      NotificationChannel(ADDED_NOTIFICATION_CHANNEL_ID,
                                                                                          getString(R.string.added_torrents_channel_name),
                                                                                          NotificationManager.IMPORTANCE_DEFAULT)))
            }

            if (Settings.showPersistentNotification) {
                startForeground()
            }

            Rpc.instance.torrentAddedListener = { id, hashString, name ->
                if (Settings.notifyOnAdded) {
                    showAddedNotification(id, hashString, name, this)
                }
            }
            Rpc.instance.torrentFinishedListener = { id, hashString, name ->
                if (Settings.notifyOnFinished) {
                    showFinishedNotification(id, hashString, name, this)
                }
            }

            instance = this
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        debug("service destroyed")
        Rpc.instance.torrentFinishedListener = null
        Rpc.instance.removeStatusListener(rpcStatusListener)
        Rpc.instance.removeErrorListener(rpcErrorListener)
        Rpc.instance.removeServerStatsUpdatedListener(serverStatsUpdatedListener)
        instance = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        debug("task removed")
        if (!Settings.showPersistentNotification) {
            Utils.shutdownApp()
        }
    }

    fun startForeground() {
        startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
        Rpc.instance.addStatusListener(rpcStatusListener)
        Rpc.instance.addErrorListener(rpcErrorListener)
        Rpc.instance.addServerStatsUpdatedListener(serverStatsUpdatedListener)
        Servers.addCurrentServerListener(currentServerListener)
    }

    fun stopForeground() {
        super.stopForeground(true)
        Rpc.instance.removeStatusListener(rpcStatusListener)
        Rpc.instance.removeErrorListener(rpcErrorListener)
        Rpc.instance.removeServerStatsUpdatedListener(serverStatsUpdatedListener)
        Servers.removeCurrentServerListener(currentServerListener)
    }

    fun stopService() {
        Rpc.instance.torrentFinishedListener = null
        if (Settings.showPersistentNotification) {
            stopForeground()
        }
        stopSelf()
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

        if (Rpc.instance.isConnected) {
            notificationBuilder.setContentText(getString(R.string.main_activity_subtitle,
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.instance.serverStats.downloadSpeed()),
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.instance.serverStats.uploadSpeed())))
        } else {
            notificationBuilder.setContentText(Rpc.instance.statusString)
        }

        if (Rpc.instance.status() == BaseRpc.Status.Disconnected) {
            notificationBuilder.addAction(
                    R.drawable.notification_connect,
                    getString(R.string.connect),
                    PendingIntent.getService(this,
                                             0,
                                             intentFor<BackgroundService>().setAction(ACTION_CONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            notificationBuilder.addAction(
                    R.drawable.notification_disconnect,
                    getString(R.string.disconnect),
                    PendingIntent.getService(this,
                                             0,
                                             intentFor<BackgroundService>().setAction(ACTION_DISCONNECT),
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        }

        notificationBuilder.addAction(
                R.drawable.notification_quit,
                getString(R.string.quit),
                PendingIntent.getService(this,
                                         0,
                                         intentFor<BackgroundService>().setAction(Intent.ACTION_SHUTDOWN),
                                         PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return notificationBuilder.build()
    }
}