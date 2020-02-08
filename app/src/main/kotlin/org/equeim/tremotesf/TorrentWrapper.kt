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

import java.util.Date

import android.content.Context

import org.equeim.libtremotesf.Torrent
import org.equeim.libtremotesf.TorrentData
import org.equeim.libtremotesf.Tracker
import org.equeim.tremotesf.utils.DecimalFormats


class TorrentWrapper(val id: Int, data: TorrentData, private val context: Context) {
    val hashString: String = data.hashString

    var name: String = data.name
        private set

    var status = data.status
        private set

    private var errorString: String = data.errorString

    val statusString: String
        get() {
            return when (status) {
                TorrentData.Status.Paused -> context.getString(R.string.torrent_paused)
                TorrentData.Status.Downloading -> context.resources.getQuantityString(R.plurals.torrent_downloading,
                        seeders,
                        seeders)
                TorrentData.Status.StalledDownloading -> context.getString(R.string.torrent_downloading_stalled)
                TorrentData.Status.Seeding -> context.resources.getQuantityString(R.plurals.torrent_seeding,
                        leechers,
                        leechers)
                TorrentData.Status.StalledSeeding -> context.getString(R.string.torrent_seeding_stalled)
                TorrentData.Status.QueuedForDownloading,
                TorrentData.Status.QueuedForSeeding -> context.getString(R.string.torrent_queued)
                TorrentData.Status.Checking -> context.getString(R.string.torrent_checking,
                        DecimalFormats.generic.format(recheckProgress * 100))
                TorrentData.Status.QueuedForChecking -> context.getString(R.string.torrent_queued_for_checking)
                TorrentData.Status.Errored -> errorString
                else -> ""
            }
        }

    var totalSize = data.totalSize
        private set
    var completedSize = data.completedSize
        private set
    var sizeWhenDone = data.sizeWhenDone
        private set
    var percentDone = data.percentDone
        private set
    var isFinished = data.leftUntilDone == 0L
        private set
    var recheckProgress = data.recheckProgress
        private set
    var eta = data.eta
        private set

    var downloadSpeed = data.downloadSpeed
        private set
    var uploadSpeed = data.uploadSpeed
        private set

    var totalDownloaded = data.totalDownloaded
        private set
    var totalUploaded = data.totalUploaded
        private set
    var ratio = data.ratio
        private set

    var seeders = data.seeders
        private set
    var leechers = data.leechers
        private set

    var addedDate: Date = data.addedDate
        private set

    var downloadDirectory: String = data.downloadDirectory
        private set

    var isChanged = false
        private set

    private val rpcTrackers = data.trackers
    val trackers = mutableListOf<String>()

    var filesLoaded = false
    var filesEnabled = false
        set(value) {
            if (value != field) {
                field = value
                //Rpc.nativeInstance.setTorrentFilesEnabled(data, value)
                if (!value) {
                    filesLoaded = false
                }
            }
        }

    var peersLoaded = false
    var peersEnabled = false
        set(value) {
            if (value != field) {
                field = value
                //Rpc.nativeInstance.setTorrentPeersEnabled(data, value)
                if (!value) {
                    peersLoaded = false
                }
            }
        }

    init {
        for (tracker: Tracker in rpcTrackers) {
            trackers.add(tracker.site())
        }
    }

    fun update(data: TorrentData) {
        name = data.name
        status = data.status
        errorString = data.errorString
        totalSize = data.totalSize
        completedSize = data.completedSize
        sizeWhenDone = data.sizeWhenDone
        percentDone = data.percentDone
        isFinished = data.leftUntilDone == 0L
        recheckProgress = data.recheckProgress
        eta = data.eta
        downloadSpeed = data.downloadSpeed
        uploadSpeed = data.uploadSpeed
        totalDownloaded = data.totalDownloaded
        totalUploaded = data.totalUploaded
        ratio = data.ratio
        seeders = data.seeders
        leechers = data.leechers
        downloadDirectory = data.downloadDirectory

        if (data.trackersAddedOrRemoved) {
            trackers.clear()
            for (tracker: Tracker in rpcTrackers) {
                trackers.add(tracker.site())
            }
        }
    }
}