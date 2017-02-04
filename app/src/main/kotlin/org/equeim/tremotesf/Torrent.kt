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

import java.net.URI
import java.text.DecimalFormat
import java.util.Date

import kotlin.reflect.KProperty

import android.content.Context

import com.google.common.net.InternetDomainName
import com.google.gson.JsonArray
import com.google.gson.JsonObject


private const val DOWNLOAD_SPEED_LIMITED = "downloadLimited"
private const val DOWNLOAD_SPEED_LIMIT = "downloadLimit"
private const val UPLOAD_SPEED_LIMITED = "uploadLimited"
private const val UPLOAD_SPEED_LIMIT = "uploadLimit"
private const val RATIO_LIMIT_MODE = "seedRatioMode"
private const val RATIO_LIMIT = "seedRatioLimit"
private const val PEERS_LIMIT = "peer-limit"
private const val HONOR_SESSION_LIMITS = "honorsSessionLimits"
private const val BANDWIDTH_PRIORITY = "bandwidthPriority"
private const val IDLE_SEEDING_LIMIT_MODE = "seedIdleMode"
private const val IDLE_SEEDING_LIMIT = "seedIdleLimit"

class Torrent(val id: Int, torrentJson: JsonObject, private val context: Context) {
    enum class Status {
        Paused,
        Downloading,
        Seeding,
        StalledDownloading,
        StalledSeeding,
        QueuedForDownloading,
        QueuedForSeeding,
        Checking,
        QueuedForChecking,
        Errored
    }

    object RatioLimitMode {
        const val GLOBAL = 0
        const val SINGLE = 1
        const val UNLIMITED = 2
    }

    object Priority {
        const val LOW = -1
        const val NORMAL = 0
        const val HIGH = 1
    }

    object IdleSeedingLimitMode {
        const val GLOBAL = 0
        const val SINGLE = 1
        const val UNLIMITED = 2
    }

    val hashString = torrentJson["hashString"].asString

    var name by SetChanged(String())

    var status by SetChanged(Status.Paused)
    //lateinit var statusString: String
    //var queuePosition = 0
    val statusString: String
        get() {
            return when (status) {
                Status.Paused -> context.getString(R.string.torrent_paused)
                Status.Downloading -> context.resources.getQuantityString(R.plurals.torrent_downloading,
                                                                          seeders,
                                                                          seeders)
                Status.StalledDownloading -> context.getString(R.string.torrent_downloading)
                Status.Seeding -> context.resources.getQuantityString(R.plurals.torrent_seeding,
                                                                      leechers,
                                                                      leechers)
                Status.StalledSeeding -> context.getString(R.string.torrent_seeding)
                Status.QueuedForDownloading,
                Status.QueuedForSeeding -> context.getString(R.string.torrent_queued)
                Status.Checking -> context.getString(R.string.torrent_checking,
                                                     DecimalFormat("0.#").format(recheckProgress))
                Status.QueuedForChecking -> context.getString(R.string.torrent_queued_for_checking)
                Status.Errored -> errorString
            }
        }
    var errorString by SetChanged(String())

    var totalSize by SetChanged(0L)
    var completedSize by SetChanged(0L)
    var leftUntilDone by SetChanged(0L)
    var sizeWhenDone by SetChanged(0L)
    var percentDone by SetChanged(0.0)
    var recheckProgress by SetChanged(0.0)

    var eta by SetChanged(0)

    var downloadSpeed by SetChanged(0L)
    var uploadSpeed by SetChanged(0L)

    var downloadSpeedLimited by OnChanged(false, DOWNLOAD_SPEED_LIMITED)
    var downloadSpeedLimit by OnChanged(0, DOWNLOAD_SPEED_LIMIT)

    var uploadSpeedLimited by OnChanged(false, UPLOAD_SPEED_LIMITED)
    var uploadSpeedLimit by OnChanged(0, UPLOAD_SPEED_LIMIT)

    var totalDownloaded = 0L

    var totalUploaded by SetChanged(0L)

    var ratio = 0.0
    var ratioLimitMode by OnChanged(RatioLimitMode.GLOBAL, RATIO_LIMIT_MODE)
    var ratioLimit by OnChanged(0.0, RATIO_LIMIT)

    var seeders = 0
    var leechers = 0
    var peersLimit by OnChanged(0, PEERS_LIMIT)

    var addedDate = 0L

    var activityDate = Date(0)
    var doneDate = Date(0)

    var honorSessionLimits by OnChanged(false, HONOR_SESSION_LIMITS)
    var bandwidthPriority by OnChanged(Priority.NORMAL, BANDWIDTH_PRIORITY)
    var idleSeedingLimitMode by OnChanged(IdleSeedingLimitMode.GLOBAL, IDLE_SEEDING_LIMIT_MODE)
    var idleSeedingLimit by OnChanged(0, IDLE_SEEDING_LIMIT)
    lateinit var downloadDirectory: String
    lateinit var creator: String
    var creationDate = Date(0)
    lateinit var comment: String

    var changed = false
    private var updating = false

    val trackers = mutableListOf<Tracker>()

    fun addTracker(announce: String) {
        Rpc.setTorrentProperty(id, "trackerAdd", arrayOf(announce), true)
    }

    fun setTracker(trackerId: Int, announce: String) {
        Rpc.setTorrentProperty(id, "trackerReplace", arrayOf(trackerId, announce), true)
    }

    fun removeTrackers(ids: IntArray) {
        Rpc.setTorrentProperty(id, "trackerRemove", ids, true)
    }

    //
    // Files
    //
    var filesUpdateEnabled = false
        set(value) {
            if (value != field) {
                field = value
                if (value) {
                    Rpc.getTorrentFiles(id, false)
                }
            }
        }

    var filesUpdated = false

    var fileJsons: JsonArray? = null
        private set
    var fileStatsJsons: JsonArray? = null
        private set

    fun updateFiles(fileJsons: JsonArray, fileStatsJsons: JsonArray) {
        this.fileJsons = fileJsons
        this.fileStatsJsons = fileStatsJsons
        filesUpdated = true
    }

    fun resetFiles() {
        fileJsons = null
        fileStatsJsons = null
    }

    var filesLoadedListener: (() -> Unit)? = null

    fun setFilesWanted(files: List<Int>, wanted: Boolean) {
        Rpc.setTorrentProperty(id,
                               if (wanted) "files-wanted" else "files-unwanted",
                               files)
    }

    fun setFilesPriority(files: List<Int>, priority: BaseTorrentFilesAdapter.Item.Priority) {
        Rpc.setTorrentProperty(id,
                               when (priority) {
                                   BaseTorrentFilesAdapter.Item.Priority.Low -> "priority-low"
                                   BaseTorrentFilesAdapter.Item.Priority.Normal -> "priority-normal"
                                   BaseTorrentFilesAdapter.Item.Priority.High -> "priority-high"
                                   BaseTorrentFilesAdapter.Item.Priority.Mixed -> "priority-normal"
                               },
                               files)
    }

    var fileRenamedListener: ((String, String) -> Unit)? = null


    var peers: JsonArray? = null
    var peersUpdateEnabled = false
        set(value) {
            if (value != field) {
                field = value
                if (value) {
                    Rpc.getTorrentPeers(id, false)
                }
            }
        }
    var peersUpdated = false
    fun updatePeers(peers: JsonArray) {
        this.peers = peers
        peersUpdated = true
    }

    var peersLoadedListener: (() -> Unit)? = null

    val updated: Boolean
        get() {
            var yes = true
            if (filesUpdateEnabled && !filesUpdated) {
                yes = false
            }
            if (peersUpdateEnabled && !peersUpdated) {
                yes = false
            }
            return yes
        }

    init {
        update(torrentJson)
    }

    fun update(torrentJson: JsonObject) {
        changed = false
        updating = true

        name = torrentJson["name"].asString

        totalSize = torrentJson["totalSize"].asLong
        completedSize = torrentJson["haveValid"].asLong
        leftUntilDone = torrentJson["leftUntilDone"].asLong
        sizeWhenDone = torrentJson["sizeWhenDone"].asLong
        percentDone = torrentJson["percentDone"].asDouble
        eta = torrentJson["eta"].asInt

        downloadSpeed = torrentJson["rateDownload"].asLong
        uploadSpeed = torrentJson["rateUpload"].asLong

        downloadSpeedLimited = torrentJson[DOWNLOAD_SPEED_LIMITED].asBoolean
        downloadSpeedLimit = torrentJson[DOWNLOAD_SPEED_LIMIT].asInt

        uploadSpeedLimited = torrentJson[UPLOAD_SPEED_LIMITED].asBoolean
        uploadSpeedLimit = torrentJson[UPLOAD_SPEED_LIMIT].asInt

        totalDownloaded = torrentJson["downloadedEver"].asLong
        totalUploaded = torrentJson["uploadedEver"].asLong
        ratio = torrentJson["uploadRatio"].asDouble
        ratioLimitMode = torrentJson[RATIO_LIMIT_MODE].asInt
        ratioLimit = torrentJson[RATIO_LIMIT].asDouble

        seeders = torrentJson["peersSendingToUs"].asInt
        leechers = torrentJson["peersGettingFromUs"].asInt

        val stalled = (seeders == 0 && leechers == 0)
        status = if (torrentJson["error"].asInt == 0) {
            when (torrentJson["status"].asInt) {
                0 -> Status.Paused
                1 -> Status.QueuedForChecking
                2 -> Status.Checking
                3 -> Status.QueuedForDownloading
                4 -> {
                    if (stalled) {
                        Status.StalledDownloading
                    } else {
                        Status.Downloading
                    }
                }
                5 -> Status.QueuedForSeeding
                6 -> {
                    if (stalled) {
                        Status.StalledSeeding
                    } else {
                        Status.Seeding
                    }
                }
                else -> Status.Errored
            }

        } else {
            Status.Errored
        }
        errorString = torrentJson["errorString"].asString

        peersLimit = torrentJson[PEERS_LIMIT].asInt

        addedDate = torrentJson["addedDate"].asLong * 1000

        val activityDateTime = torrentJson["activityDate"].asLong
        activityDate.time = if (activityDateTime > 0) {
            activityDateTime * 1000
        } else {
            0
        }

        val doneDateTime = torrentJson["doneDate"].asLong
        doneDate.time = if (doneDateTime > 0) {
            doneDateTime * 1000
        } else {
            0
        }

        honorSessionLimits = torrentJson[HONOR_SESSION_LIMITS].asBoolean
        bandwidthPriority = torrentJson[BANDWIDTH_PRIORITY].asInt
        idleSeedingLimitMode = torrentJson[IDLE_SEEDING_LIMIT_MODE].asInt
        idleSeedingLimit = torrentJson[IDLE_SEEDING_LIMIT].asInt
        downloadDirectory = torrentJson["downloadDir"].asString
        creator = torrentJson["creator"].asString

        val creationDateTime = torrentJson["dateCreated"].asLong
        creationDate.time = if (creationDateTime > 0) {
            creationDateTime * 1000
        } else {
            0
        }

        comment = torrentJson["comment"].asString

        val newTrackers = mutableListOf<Tracker>()
        val trackerStats = torrentJson.getAsJsonArray("trackerStats")
        for (jsonElement in trackerStats) {
            val trackerJson = jsonElement.asJsonObject
            val id = trackerJson["id"].asInt
            var tracker = trackers.find { it.id == id }
            if (tracker == null) {
                tracker = Tracker(id, context)
            }
            tracker.update(trackerJson)
            newTrackers.add(tracker)
        }
        trackers.clear()
        trackers.addAll(newTrackers)

        updating = false
    }

    private class SetChanged<T : Any>(private var field: T) {
        operator fun getValue(thisRef: Torrent, property: KProperty<*>) = field

        operator fun setValue(thisRef: Torrent, property: KProperty<*>, value: T) {
            if (value != field) {
                field = value
                thisRef.changed = true
            }
        }
    }

    private class OnChanged<T : Any>(private var field: T, private val key: String) {
        operator fun getValue(thisRef: Torrent, property: KProperty<*>) = field

        operator fun setValue(thisRef: Torrent, property: KProperty<*>, value: T) {
            if (value != field) {
                field = value
                if (!thisRef.updating) {
                    Rpc.setTorrentProperty(thisRef.id, key, value)
                }
            }
        }
    }
}

class Tracker(val id: Int, private val context: Context) {
    enum class Status {
        Inactive,
        Active,
        Queued,
        Updating,
        Error
    }

    var announce = String()
        private set(value) {
            if (value != field) {
                field = value
                changed = true
            }
        }

    lateinit var site: String

    var status = Status.Inactive
        private set(value) {
            if (value != field) {
                field = value
                changed = true
            }
        }

    private var errorMessage = String()
        private set(value) {
            if (value != field) {
                field = value
                changed = true
            }
        }

    val statusString: String
        get() {
            return when (status) {
                Status.Inactive -> context.getString(R.string.tracker_inactive)
                Status.Active -> context.getString(R.string.tracker_active)
                Status.Queued -> context.getString(R.string.tracker_queued)
                Status.Updating -> context.getString(R.string.tracker_updating)
                Status.Error -> {
                    if (errorMessage.isEmpty()) {
                        context.getString(R.string.error)
                    } else {
                        context.getString(R.string.tracker_error, errorMessage)
                    }
                }
            }
        }

    var peers = 0
        private set(value) {
            if (value != field) {
                field = value
                changed = true
            }
        }

    var nextUpdate = -1L
        private set(value) {
            if (value != field) {
                field = value
                changed = true
            }
        }


    var changed = false

    fun update(trackerJson: JsonObject) {
        changed = false

        announce = trackerJson["announce"].asString

        val host = URI(announce).host
        site = try {
            val domain = InternetDomainName.from(URI(announce).host)
            if (domain.hasPublicSuffix()) {
                domain.topPrivateDomain().toString()
            } else {
                host
            }
        } catch (error: IllegalArgumentException) {
            host
        }

        val scrapeError = (!trackerJson["lastScrapeSucceeded"].asBoolean && trackerJson["lastScrapeTime"].asInt != 0)
        val announceError = (!trackerJson["lastAnnounceSucceeded"].asBoolean &&
                trackerJson["lastAnnounceTime"].asInt != 0)
        if (scrapeError || announceError) {
            status = Status.Error
            if (scrapeError) {
                errorMessage = trackerJson["lastScrapeResult"].asString
            } else {
                errorMessage = trackerJson["lastAnnounceResult"].asString
            }
        } else {
            status = when (trackerJson["announceState"].asInt) {
                0 -> Status.Inactive
                1 -> Status.Active
                2 -> Status.Queued
                3 -> Status.Updating
                else -> Status.Error
            }
            errorMessage = String()
        }

        peers = trackerJson["lastAnnouncePeerCount"].asInt

        val time = trackerJson["nextAnnounceTime"].asLong
        nextUpdate = if (time < 0) {
            -1
        } else {
            (Date(time * 1000).time - Date().time) / 1000
        }
    }
}