/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service

import android.content.Context
import android.content.Intent

import android.os.Build

import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder

import org.equeim.tremotesf.mainactivity.MainActivity
import org.equeim.tremotesf.torrentpropertiesactivity.TorrentPropertiesActivity
import org.equeim.tremotesf.utils.Logger
import org.equeim.tremotesf.utils.Utils


private const val PERSISTENT_NOTIFICATION_ID = 1
private const val ACTION_CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
private const val ACTION_DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"

class BackgroundService : Service() {
    companion object {
        var instance: BackgroundService? = null
            private set
    }

    private lateinit var notificationManager: NotificationManager

    private val rpcStatusListener = { status: Rpc.Status -> updatePersistentNotification() }
    private val rpcUpdatedListener = { updatePersistentNotification() }
    private val rpcErrorListener = { error: Rpc.Error -> updatePersistentNotification() }
    private val currentServerListener = { updatePersistentNotification() }

    override fun onBind(intent: Intent) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (instance == this) {
            when (intent?.action) {
                ACTION_CONNECT -> Rpc.connect()
                ACTION_DISCONNECT -> Rpc.disconnect()
                Intent.ACTION_SHUTDOWN -> Utils.shutdownApp(this)
            }
        } else {
            Logger.d("service started")

            Utils.initApp(applicationContext)

            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Settings.showPersistentNotification) {
                startForeground()
            }

            Rpc.torrentFinishedListener = { torrent -> showFinishedNotification(torrent) }

            instance = this
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d("service destroyed")

        stopForeground()
        Rpc.torrentFinishedListener = null

        instance = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.d("task removed")
        if (!Settings.showPersistentNotification) {
            Utils.shutdownApp(this)
        }
    }

    fun startForeground() {
        startForeground(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
        Rpc.addStatusListener(rpcStatusListener)
        Rpc.addErrorListener(rpcErrorListener)
        Rpc.addUpdatedListener(rpcUpdatedListener)
        Servers.addCurrentServerListener(currentServerListener)
    }

    fun stopForeground() {
        stopForeground(true)
        Rpc.removeStatusListener(rpcStatusListener)
        Rpc.removeErrorListener(rpcErrorListener)
        Rpc.removeUpdatedListener(rpcUpdatedListener)
        Servers.removeCurrentServerListener(currentServerListener)
    }

    fun updatePersistentNotification() {
        notificationManager.notify(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification())
    }

    private fun buildPersistentNotification(): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java)

        val notificationBuilder = NotificationCompat.Builder(applicationContext)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        mainActivityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                ))
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

        if (Rpc.connected) {
            notificationBuilder.setContentText(getString(R.string.main_activity_subtitle,
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.serverStats.downloadSpeed),
                                                         Utils.formatByteSpeed(this,
                                                                               Rpc.serverStats.uploadSpeed)))
        } else {
            notificationBuilder.setContentText(Rpc.statusString)
        }

        if (Rpc.connected) {
            val intent = Intent(this, BackgroundService::class.java)
            intent.action = ACTION_DISCONNECT
            notificationBuilder.addAction(
                    R.drawable.notification_disconnect,
                    getString(R.string.disconnect),
                    PendingIntent.getService(this,
                                             0,
                                             intent,
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        } else if (Rpc.canConnect) {
            val intent = Intent(this, BackgroundService::class.java)
            intent.action = ACTION_CONNECT
            notificationBuilder.addAction(
                    R.drawable.notification_connect,
                    getString(R.string.connect),
                    PendingIntent.getService(this,
                                             0,
                                             intent,
                                             PendingIntent.FLAG_UPDATE_CURRENT))
        }

        val shutdownIntent = Intent(this, BackgroundService::class.java)
        shutdownIntent.action = Intent.ACTION_SHUTDOWN
        notificationBuilder.addAction(
                R.drawable.notification_quit,
                getString(R.string.quit),
                PendingIntent.getService(this, 0, shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return notificationBuilder.build()
    }

    fun showFinishedNotification(torrent: Torrent) {
        val intent = Intent(this, TorrentPropertiesActivity::class.java)
        intent.putExtra(TorrentPropertiesActivity.HASH, torrent.hashString)
        intent.putExtra(TorrentPropertiesActivity.NAME, torrent.name)

        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addParentStack(TorrentPropertiesActivity::class.java)
        stackBuilder.addNextIntent(intent)

        notificationManager.notify(torrent.id,
                                   NotificationCompat.Builder(this)
                                           .setSmallIcon(R.drawable.notification_icon)
                                           .setContentTitle(getString(R.string.torrent_finished))
                                           .setContentText(torrent.name)
                                           .setContentIntent(stackBuilder.getPendingIntent(0,
                                                                                           PendingIntent.FLAG_UPDATE_CURRENT))
                                           .setAutoCancel(true)
                                           .setDefaults(Notification.DEFAULT_ALL)
                                           .build())
    }
}