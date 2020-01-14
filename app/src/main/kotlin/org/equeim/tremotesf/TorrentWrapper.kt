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


class TorrentWrapper(val id: Int, val torrent: Torrent, private val context: Context) {
    val hashString: String = torrent.hashString()

    var name: String = torrent.name()
        private set

    var status = torrent.status()
        private set

    private var errorString: String = torrent.errorString()

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

    var totalSize = torrent.totalSize()
        private set
    var completedSize = torrent.completedSize()
        private set
    var sizeWhenDone = torrent.sizeWhenDone()
        private set
    var percentDone = torrent.percentDone()
        private set
    var isFinished = torrent.isFinished
        private set
    var recheckProgress = torrent.recheckProgress()
        private set
    var eta = torrent.eta()
        private set

    var downloadSpeed = torrent.downloadSpeed()
        private set
    var uploadSpeed = torrent.uploadSpeed()
        private set

    var totalDownloaded = torrent.totalDownloaded()
        private set
    var totalUploaded = torrent.totalUploaded()
        private set
    var ratio = torrent.ratio()
        private set

    var seeders = torrent.seeders()
        private set
    var leechers = torrent.leechers()
        private set

    var addedDate: Date = torrent.addedDate()
        private set

    var downloadDirectory: String = torrent.downloadDirectory()
        private set

    var isChanged = false
        private set

    private val rpcTrackers = torrent.trackers()
    val trackers = mutableListOf<String>()

    var filesLoaded = false
    var filesEnabled = false
        set(value) {
            if (value != field) {
                field = value
                Rpc.nativeInstance.setTorrentFilesEnabled(torrent, value)
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
                Rpc.nativeInstance.setTorrentPeersEnabled(torrent, value)
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

    fun update() {
        isChanged = torrent.isChanged
        if (isChanged) {
            name = torrent.name()
            status = torrent.status()
            errorString = torrent.errorString()
            totalSize = torrent.totalSize()
            completedSize = torrent.completedSize()
            sizeWhenDone = torrent.sizeWhenDone()
            percentDone = torrent.percentDone()
            isFinished = torrent.isFinished
            recheckProgress = torrent.recheckProgress()
            eta = torrent.eta()
            downloadSpeed = torrent.downloadSpeed()
            uploadSpeed = torrent.uploadSpeed()
            totalDownloaded = torrent.totalDownloaded()
            totalUploaded = torrent.totalUploaded()
            ratio = torrent.ratio()
            seeders = torrent.seeders()
            leechers = torrent.leechers()
            downloadDirectory = torrent.downloadDirectory()

            if (torrent.isTrackersAddedOrRemoved) {
                trackers.clear()
                for (tracker: Tracker in rpcTrackers) {
                    trackers.add(tracker.site())
                }
            }
        }
    }
}