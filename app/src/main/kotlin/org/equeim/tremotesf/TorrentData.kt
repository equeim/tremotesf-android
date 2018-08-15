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

import java.text.DecimalFormat
import java.util.Date

import android.content.Context

import org.equeim.libtremotesf.Torrent

class TorrentData(val torrent: Torrent, private val context: Context) {
    val id = torrent.id()
    val hashString: String = torrent.hashString()

    var name: String = torrent.name()
        private set

    var status = torrent.status()
        private set

    private var errorString: String = torrent.errorString()

    val statusString: String
        get() {
            return when (status) {
                Torrent.Status.Paused -> context.getString(R.string.torrent_paused)
                Torrent.Status.Downloading -> context.resources.getQuantityString(R.plurals.torrent_downloading,
                        seeders,
                        seeders)
                Torrent.Status.StalledDownloading -> context.getString(R.string.torrent_downloading_stalled)
                Torrent.Status.Seeding -> context.resources.getQuantityString(R.plurals.torrent_seeding,
                        leechers,
                        leechers)
                Torrent.Status.StalledSeeding -> context.getString(R.string.torrent_seeding_stalled)
                Torrent.Status.QueuedForDownloading,
                Torrent.Status.QueuedForSeeding -> context.getString(R.string.torrent_queued)
                Torrent.Status.Checking -> context.getString(R.string.torrent_checking,
                        DecimalFormat("0.#").format(recheckProgress))
                Torrent.Status.QueuedForChecking -> context.getString(R.string.torrent_queued_for_checking)
                Torrent.Status.Errored -> errorString
                else -> ""
            }
        }

    var totalSize = torrent.totalSize()
        private set
    var completedSize = torrent.completedSize()
        private set
    var leftUntilDone = torrent.leftUntilDone()
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

    private val tTrackers = torrent.trackers()
    val trackers = mutableListOf<String>()

    init {
        for (i in 0 until tTrackers.size()) {
            trackers.add(tTrackers[i.toInt()].site())
        }
    }

    fun update() {
        if (torrent.isChanged) {
            name = torrent.name()
            status = torrent.status()
            errorString = torrent.errorString()
            totalSize = torrent.totalSize()
            completedSize = torrent.completedSize()
            leftUntilDone = torrent.leftUntilDone()
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
        }

        trackers.clear()
        for (i in 0 until tTrackers.size()) {
            trackers.add(tTrackers[i.toInt()].site())
        }
    }
}