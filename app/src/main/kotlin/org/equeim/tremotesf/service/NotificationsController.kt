package org.equeim.tremotesf.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.navigation.NavDeepLinkBuilder
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentArgs
import org.equeim.tremotesf.ui.utils.FormatUtils
import timber.log.Timber

class NotificationsController(private val context: Context) {
    private val notificationManager = context.getSystemService<NotificationManager>().also {
        if (it == null) {
            Timber.e("NotificationManager is null")
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannels(
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
                    )
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

    private suspend fun isNotifyOnAddedEnabled(sinceLastConnection: Boolean): Boolean {
        return if (sinceLastConnection) {
            Settings.notifyOnAddedSinceLastConnection
        } else {
            Settings.notifyOnAdded
        }.get()
    }

    suspend fun showTorrentFinishedNotification(
        torrentId: Int,
        hashString: String,
        torrentName: String,
        sinceLastConnection: Boolean
    ) {
        if (isNotifyOnFinishedEnabled(sinceLastConnection)) {
            showTorrentNotification(torrentId, hashString, torrentName, FINISHED_NOTIFICATION_CHANNEL_ID, R.string.torrent_finished)
        }
    }

    suspend fun showTorrentAddedNotification(
        torrentId: Int,
        hashString: String,
        torrentName: String,
        sinceLastConnection: Boolean
    ) {
        if (isNotifyOnAddedEnabled(sinceLastConnection)) {
            showTorrentNotification(torrentId, hashString, torrentName, ADDED_NOTIFICATION_CHANNEL_ID, R.string.torrent_added)
        }
    }

    private fun showTorrentNotification(
        torrentId: Int,
        hashString: String,
        torrentName: String,
        notificationChannel: String,
        @StringRes notificationTitle: Int
    ) {
        Timber.i("showTorrentNotification() called with: torrentId = $torrentId, hashString = $hashString, torrentName = $torrentName, notificationChannel = $notificationChannel, notificationTitle = $notificationTitle")

        if (notificationManager == null) {
            Timber.e("showTorrentNotification: NotificationManager is null")
            return
        }

        notificationManager.notify(
            torrentId,
            NotificationCompat.Builder(context, notificationChannel)
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
                .setDefaults(Notification.DEFAULT_ALL)
                .build()
        )
    }

    fun buildPersistentNotification(rpc: Rpc, status: Rpc.Status = rpc.status.value): Notification {
        val notificationBuilder =
            NotificationCompat.Builder(context, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(
                    NavDeepLinkBuilder(context)
                        .setGraph(R.navigation.nav_main)
                        .setDestination(R.id.torrents_list_fragment)
                        .createPendingIntent()
                )
                .setOngoing(true)
                .setShowWhen(false)

        val currentServer = GlobalServers.currentServer.value
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

        if (status.isConnected) {
            val stats = rpc.serverStats.value
            notificationBuilder.setContentText(
                context.getString(
                    R.string.main_activity_subtitle,
                    FormatUtils.formatByteSpeed(
                        context,
                        stats.downloadSpeed
                    ),
                    FormatUtils.formatByteSpeed(
                        context,
                        stats.uploadSpeed
                    )
                )
            )
        } else {
            notificationBuilder.setContentText(status.statusString)
        }

        if (status.connectionState == RpcConnectionState.Disconnected) {
            notificationBuilder.addAction(
                R.drawable.notification_connect,
                context.getText(R.string.connect),
                ForegroundService.getPendingIntent(context, PersistentNotificationActions.CONNECT)
            )
        } else {
            notificationBuilder.addAction(
                R.drawable.notification_disconnect,
                context.getText(R.string.disconnect),
                ForegroundService.getPendingIntent(context, PersistentNotificationActions.DISCONNECT)
            )
        }

        notificationBuilder.addAction(
            R.drawable.notification_quit,
            context.getText(R.string.quit),
            ForegroundService.getPendingIntent(context, PersistentNotificationActions.SHUTDOWN_APP)
        )

        return notificationBuilder.build()
    }

    fun updatePersistentNotification(rpc: Rpc, status: Rpc.Status) {
        if (notificationManager != null) {
            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, buildPersistentNotification(rpc, status))
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
    }
}
