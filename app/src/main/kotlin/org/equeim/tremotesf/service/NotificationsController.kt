// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.navigation.NavDeepLinkBuilder
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.rpc.requests.SessionStatsResponseArguments
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentArgs
import org.equeim.tremotesf.ui.utils.FormatUtils
import timber.log.Timber
import kotlin.random.Random

class NotificationsController(private val context: Context) {
    private val notificationManager = context.getSystemService<NotificationManager>().also {
        if (it == null) {
            Timber.e("NotificationManager is null")
        }
    }

    private val random = Random(System.nanoTime())

    init {
        if (notificationManager != null) {
            notificationManager.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        FINISHED_NOTIFICATION_CHANNEL_ID,
                        context.getText(R.string.finished_torrents_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                        ADDED_NOTIFICATION_CHANNEL_ID,
                        context.getText(R.string.added_torrents_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                        PERSISTENT_NOTIFICATION_CHANNEL_ID,
                        context.getText(R.string.persistent_notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            )
            Timber.i("init: created notification channels")
        }
    }

    suspend fun isTorrentNotificationsEnabled(sinceLastConnection: Boolean): Boolean {
        return isNotifyOnFinishedEnabled(sinceLastConnection) || isNotifyOnAddedEnabled(sinceLastConnection)
    }

    suspend fun isNotifyOnFinishedEnabled(sinceLastConnection: Boolean): Boolean {
        return if (sinceLastConnection) {
            Settings.notifyOnFinishedSinceLastConnection
        } else {
            Settings.notifyOnFinished
        }.get()
    }

    suspend fun isNotifyOnAddedEnabled(sinceLastConnection: Boolean): Boolean {
        return if (sinceLastConnection) {
            Settings.notifyOnAddedSinceLastConnection
        } else {
            Settings.notifyOnAdded
        }.get()
    }

    fun showTorrentFinishedNotification(hashString: String, torrentName: String) {
        showTorrentNotification(hashString, torrentName, FINISHED_NOTIFICATION_CHANNEL_ID, R.string.torrent_finished)
    }

    fun showTorrentAddedNotification(hashString: String, torrentName: String) {
        showTorrentNotification(hashString, torrentName, ADDED_NOTIFICATION_CHANNEL_ID, R.string.torrent_added)
    }

    private fun showTorrentNotification(
        hashString: String,
        torrentName: String,
        notificationChannel: String,
        @StringRes notificationTitle: Int,
    ) {
        Timber.i("showTorrentNotification() called with: hashString = $hashString, torrentName = $torrentName, notificationChannel = $notificationChannel, notificationTitle = $notificationTitle")

        if (notificationManager == null) {
            Timber.e("showTorrentNotification: NotificationManager is null")
            return
        }

        notificationManager.notify(
            random.nextInt(),
            Notification.Builder(context, notificationChannel)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(context.getText(notificationTitle))
                .setContentText(torrentName)
                .setContentIntent(
                    NavDeepLinkBuilder(context)
                        .setGraph(R.navigation.nav_main)
                        .setDestination(R.id.torrent_properties_fragment)
                        .setArguments(TorrentPropertiesFragmentArgs(hashString, torrentName).toBundle())
                        .createPendingIntent()
                )
                .setAutoCancel(true)
                .build()
        )
    }

    fun buildPersistentNotification(
        currentServer: Server?,
        sessionStats: RpcRequestState<SessionStatsResponseArguments>,
    ): Notification {
        val notificationBuilder =
            Notification.Builder(context, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(
                    NavDeepLinkBuilder(context)
                        .setGraph(R.navigation.nav_main)
                        .setDestination(R.id.torrents_list_fragment)
                        .createPendingIntent()
                )
                .setOngoing(true)
                .setShowWhen(false)
                .setDeleteIntent(
                    ForegroundService.getPendingIntent(
                        context,
                        PersistentNotificationActions.STOP_UPDATING_NOTIFICATION
                    )
                )

        if (currentServer != null) {
            notificationBuilder.setContentTitle(
                context.getString(
                    R.string.current_server_string,
                    currentServer.name,
                    currentServer.address
                )
            )
        } else {
            notificationBuilder.setContentTitle(context.getText(R.string.no_servers))
        }

        notificationBuilder.setContentText(
            when (sessionStats) {
                is RpcRequestState.Loading -> context.getText(R.string.connecting)
                is RpcRequestState.Loaded -> context.getString(
                    R.string.main_activity_subtitle,
                    FormatUtils.formatTransferRate(
                        context,
                        sessionStats.response.downloadSpeed
                    ),
                    FormatUtils.formatTransferRate(
                        context,
                        sessionStats.response.uploadSpeed
                    )
                )

                is RpcRequestState.Error -> sessionStats.error.getErrorString(context)
            }
        )

        if ((sessionStats as? RpcRequestState.Error)?.error is RpcRequestError.ConnectionDisabled) {
            notificationBuilder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.notification_connect),
                    context.getText(R.string.connect),
                    ForegroundService.getPendingIntent(context, PersistentNotificationActions.CONNECT)
                ).build()
            )
        } else {
            notificationBuilder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.notification_disconnect),
                    context.getText(R.string.disconnect),
                    ForegroundService.getPendingIntent(context, PersistentNotificationActions.DISCONNECT)
                ).build()
            )
        }

        notificationBuilder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.notification_quit),
                context.getText(R.string.quit),
                ForegroundService.getPendingIntent(context, PersistentNotificationActions.SHUTDOWN_APP)
            ).build()
        )

        return notificationBuilder.build()
    }

    fun updatePersistentNotification(
        currentServer: Server?,
        sessionStats: RpcRequestState<SessionStatsResponseArguments>,
    ) {
        if (notificationManager != null) {
            Timber.d("Updating persistent notification")
            notificationManager.notify(
                PERSISTENT_NOTIFICATION_ID,
                buildPersistentNotification(currentServer, sessionStats)
            )
        } else {
            Timber.e("updatePersistentNotification: NotificationManager is null")
        }
    }

    companion object {
        private const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
        private const val ADDED_NOTIFICATION_CHANNEL_ID = "added"

        private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent"
        const val PERSISTENT_NOTIFICATION_ID = Int.MAX_VALUE
    }

    object PersistentNotificationActions {
        const val CONNECT = "org.equeim.tremotesf.ACTION_CONNECT"
        const val DISCONNECT = "org.equeim.tremotesf.ACTION_DISCONNECT"
        const val SHUTDOWN_APP = "org.equeim.tremotesf.ACTION_SHUTDOWN_APP"
        const val STOP_UPDATING_NOTIFICATION = "org.equeim.tremotesf.ACTION_STOP_UPDATING_NOTIFICATION"
    }
}
