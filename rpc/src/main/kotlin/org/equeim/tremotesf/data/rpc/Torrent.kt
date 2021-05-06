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

package org.equeim.tremotesf.data.rpc

import org.equeim.libtremotesf.StringsVector
import org.equeim.libtremotesf.TorrentData
import org.equeim.libtremotesf.Tracker


class Torrent(val data: TorrentData, private val rpc: Rpc, prevTorrent: Torrent? = null) {
    val id = data.id
    val hashString: String = data.hashString

    val name: String = data.name

    val status = data.status
    val errorString: String = data.errorString

    val totalSize = data.totalSize
    val completedSize = data.completedSize
    val sizeWhenDone = data.sizeWhenDone
    val percentDone = data.percentDone
    val isFinished = data.leftUntilDone == 0L
    val recheckProgress = data.recheckProgress
    val eta = data.eta

    val downloadSpeed = data.downloadSpeed
    val uploadSpeed = data.uploadSpeed

    val totalDownloaded = data.totalDownloaded
    val totalUploaded = data.totalUploaded
    val ratio = data.ratio

    val seeders = data.seeders
    val leechers = data.leechers

    val addedDateTime: Long = data.addedDateTime

    val downloadDirectory: String = data.downloadDirectory

    val trackers: List<Tracker> = data.trackers
    val trackerSites: List<String>

    var filesEnabled: Boolean = prevTorrent?.filesEnabled ?: false
        @Synchronized get
        @Synchronized set(value) {
            if (value != field) {
                field = value
                rpc.nativeInstance.setTorrentFilesEnabled(data, value)
            }
        }

    var peersEnabled: Boolean = prevTorrent?.peersEnabled ?: false
        @Synchronized get
        @Synchronized set(value) {
            if (value != field) {
                field = value
                rpc.nativeInstance.setTorrentPeersEnabled(data, value)
            }
        }

    @Volatile
    var isChanged = true

    init {
        trackerSites = if (prevTorrent != null && !data.trackersAddedOrRemoved) {
            prevTorrent.trackerSites
        } else {
            trackers.map(Tracker::site)
        }
    }

    fun setDownloadSpeedLimited(limited: Boolean) {
        rpc.nativeInstance.setTorrentDownloadSpeedLimited(data, limited)
    }

    fun setDownloadSpeedLimit(limit: Int) {
        rpc.nativeInstance.setTorrentDownloadSpeedLimit(data, limit)
    }

    fun setUploadSpeedLimited(limited: Boolean) {
        rpc.nativeInstance.setTorrentUploadSpeedLimited(data, limited)
    }

    fun setUploadSpeedLimit(limit: Int) {
        rpc.nativeInstance.setTorrentUploadSpeedLimit(data, limit)
    }

    fun setRatioLimitMode(mode: Int) {
        rpc.nativeInstance.setTorrentRatioLimitMode(data, mode)
    }

    fun setRatioLimit(limit: Double) {
        rpc.nativeInstance.setTorrentRatioLimit(data, limit)
    }

    fun setPeersLimit(limit: Int) {
        rpc.nativeInstance.setTorrentPeersLimit(data, limit)
    }

    fun setHonorSessionLimits(honor: Boolean) {
        rpc.nativeInstance.setTorrentHonorSessionLimits(data, honor)
    }

    fun setBandwidthPriority(priority: Int) {
        rpc.nativeInstance.setTorrentBandwidthPriority(data, priority)
    }

    fun setIdleSeedingLimitMode(mode: Int) {
        rpc.nativeInstance.setTorrentIdleSeedingLimitMode(data, mode)
    }

    fun setIdleSeedingLimit(limit: Int) {
        rpc.nativeInstance.setTorrentIdleSeedingLimit(data, limit)
    }

    fun setFilesWanted(files: IntArray, wanted: Boolean) {
        rpc.nativeInstance.setTorrentFilesWanted(data, files, wanted)
    }

    fun setFilesPriority(files: IntArray, priority: Int) {
        rpc.nativeInstance.setTorrentFilesPriority(data, files, priority)
    }

    fun addTrackers(announceUrls: List<String>) {
        val vector = StringsVector()
        vector.reserve(announceUrls.size.toLong())
        vector.addAll(announceUrls)
        rpc.nativeInstance.torrentAddTrackers(data, vector)
        vector.delete()
    }

    fun setTracker(trackerId: Int, announce: String) {
        rpc.nativeInstance.torrentSetTracker(data, trackerId, announce)
    }

    fun removeTrackers(ids: IntArray) {
        rpc.nativeInstance.torrentRemoveTrackers(data, ids)
    }
}
