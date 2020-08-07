/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

import kotlin.properties.Delegates

import android.content.Context

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentData
import org.equeim.libtremotesf.Tracker
import org.equeim.tremotesf.utils.DecimalFormats


class Torrent(data: TorrentData, private val context: Context, prevTorrent: Torrent? = null) {
    var data = data
        private set

    val id = data.id
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

    var addedDateTime: Long = data.addedDateTime
        private set

    var downloadDirectory: String = data.downloadDirectory
        private set

    var trackers: List<Tracker> = data.trackers
        private set
    val trackerSites: List<String>

    var filesEnabled: Boolean by Delegates.observable(prevTorrent?.filesEnabled ?: false) { _, oldFilesEnabled, filesEnabled ->
        if (filesEnabled != oldFilesEnabled) {
            Rpc.nativeInstance.setTorrentFilesEnabled(data, filesEnabled)
        }
    }

    var peersEnabled: Boolean by Delegates.observable(prevTorrent?.peersEnabled ?: false) { _, oldPeersEnabled, peersEnabled ->
        if (peersEnabled != oldPeersEnabled) {
            Rpc.nativeInstance.setTorrentPeersEnabled(data, peersEnabled)
        }
    }

    var isChanged = true

    init {
        trackerSites = if (prevTorrent != null && !data.trackersAddedOrRemoved) {
            prevTorrent.trackerSites
        } else {
            trackers.map(Tracker::site)
        }
    }

    fun setDownloadSpeedLimited(limited: Boolean) {
        Rpc.nativeInstance.setTorrentDownloadSpeedLimited(data, limited)
    }

    fun setDownloadSpeedLimit(limit: Int) {
        Rpc.nativeInstance.setTorrentDownloadSpeedLimit(data, limit)
    }

    fun setUploadSpeedLimited(limited: Boolean) {
        Rpc.nativeInstance.setTorrentUploadSpeedLimited(data, limited)
    }

    fun setUploadSpeedLimit(limit: Int) {
        Rpc.nativeInstance.setTorrentUploadSpeedLimit(data, limit)
    }

    fun setRatioLimitMode(mode: Int) {
        Rpc.nativeInstance.setTorrentRatioLimitMode(data, mode)
    }

    fun setRatioLimit(limit: Double) {
        Rpc.nativeInstance.setTorrentRatioLimit(data, limit)
    }

    fun setPeersLimit(limit: Int) {
        Rpc.nativeInstance.setTorrentPeersLimit(data, limit)
    }

    fun setHonorSessionLimits(honor: Boolean) {
        Rpc.nativeInstance.setTorrentHonorSessionLimits(data, honor)
    }

    fun setBandwidthPriority(priority: Int) {
        Rpc.nativeInstance.setTorrentBandwidthPriority(data, priority)
    }

    fun setIdleSeedingLimitMode(mode: Int) {
        Rpc.nativeInstance.setTorrentIdleSeedingLimitMode(data, mode)
    }

    fun setIdleSeedingLimit(limit: Int) {
        Rpc.nativeInstance.setTorrentIdleSeedingLimit(data, limit)
    }

    fun setFilesWanted(files: IntArray, wanted: Boolean) {
        Rpc.nativeInstance.setTorrentFilesWanted(data, files, wanted)
    }

    fun setFilesPriority(files: IntArray, priority: Int) {
        Rpc.nativeInstance.setTorrentFilesPriority(data, files, priority)
    }

    fun addTrackers(announceUrls: List<String>) {
        val vector = StringsVector()
        vector.reserve(announceUrls.size.toLong())
        vector.addAll(announceUrls)
        Rpc.nativeInstance.torrentAddTrackers(data, vector)
        vector.delete()
    }

    fun setTracker(trackerId: Int, announce: String) {
        Rpc.nativeInstance.torrentSetTracker(data, trackerId, announce)
    }

    fun removeTrackers(ids: IntArray) {
        Rpc.nativeInstance.torrentRemoveTrackers(data, ids)
    }
}