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
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentArgs
import org.equeim.tremotesf.utils.Logger
import kotlin.math.sin

class NotificationsController(private val context: Context) : Logger {
    private val notificationManager by lazy {
        context.getSystemService<NotificationManager>().also {
            if (it == null) {
                error("NotificationManager is null")
            }
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
                    )
                )
            )
            info("init: created notification channels")
        }
    }

    fun isTorrentNotificationsEnabled(sinceLastConnection: Boolean): Boolean {
        return isNotifyOnFinishedEnabled(sinceLastConnection) || isNotifyOnAddedEnabled(sinceLastConnection)
    }

    fun isNotifyOnFinishedEnabled(sinceLastConnection: Boolean): Boolean {
        return if (sinceLastConnection) {
            Settings.notifyOnFinishedSinceLastConnection
        } else {
            Settings.notifyOnFinished
        }
    }

    fun isNotifyOnAddedEnabled(sinceLastConnection: Boolean): Boolean {
        return if (sinceLastConnection) {
            Settings.notifyOnAddedSinceLastConnection
        } else {
            Settings.notifyOnAdded
        }
    }

    fun showTorrentFinishedNotification(
        torrentId: Int,
        hashString: String,
        torrentName: String,
        sinceLastConnection: Boolean
    ) {
        if (isNotifyOnFinishedEnabled(sinceLastConnection)) {
            showTorrentNotification(torrentId, hashString, torrentName, FINISHED_NOTIFICATION_CHANNEL_ID, R.string.torrent_finished)
        }
    }

    fun showTorrentAddedNotification(
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
        info("showTorrentNotification() called with: torrentId = $torrentId, hashString = $hashString, torrentName = $torrentName, notificationChannel = $notificationChannel, notificationTitle = $notificationTitle")

        val notificationManager = this.notificationManager
        if (notificationManager == null) {
            error("showTorrentNotification: NotificationManager is null")
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

    private companion object {
        const val FINISHED_NOTIFICATION_CHANNEL_ID = "finished"
        const val ADDED_NOTIFICATION_CHANNEL_ID = "added"
    }
}
