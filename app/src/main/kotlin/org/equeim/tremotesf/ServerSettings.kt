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

import kotlin.reflect.KProperty
import com.google.gson.JsonObject


private const val DOWNLOAD_DIRECTORY = "download-dir"
private const val START_ADDED_TORRENTS = "start-added-torrents"
private const val RENAME_INCOMPLETE_FILES = "rename-partial-files"
private const val INCOMPLETE_FILES_DIRECTORY_ENABLED = "incomplete-dir-enabled"
private const val INCOMPLETE_FILES_DIRECTORY = "incomplete-dir"

private const val RATIO_LIMITED = "seedRatioLimited"
private const val RATIO_LIMIT = "seedRatioLimit"
private const val IDLE_SEEDING_LIMITED = "idle-seeding-limit-enabled"
private const val IDLE_SEEDING_LIMIT = "idle-seeding-limit"

private const val DOWNLOAD_QUEUE_ENABLED = "download-queue-enabled"
private const val DOWNLOAD_QUEUE_SIZE = "download-queue-size"
private const val SEED_QUEUE_ENABLED = "seed-queue-enabled"
private const val SEED_QUEUE_SIZE = "seed-queue-size"
private const val IDLE_QUEUE_LIMITED = "queue-stalled-enabled"
private const val IDLE_QUEUE_LIMIT = "queue-stalled-minutes"

private const val DOWNLOAD_SPEED_LIMITED = "speed-limit-down-enabled"
private const val DOWNLOAD_SPEED_LIMIT = "speed-limit-down"
private const val UPLOAD_SPEED_LIMITED = "speed-limit-up-enabled"
private const val UPLOAD_SPEED_LIMIT = "speed-limit-up"
private const val ALTERNATIVE_SPEED_LIMITS_ENABLED = "alt-speed-enabled"
private const val ALTERNATIVE_DOWNLOAD_SPEED_LIMIT = "alt-speed-down"
private const val ALTERNATIVE_UPLOAD_SPEED_LIMIT = "alt-speed-up"
private const val ALTERNATIVE_SPEED_LIMITS_SCHEDULED = "alt-speed-time-enabled"
private const val ALTERNATIVE_SPEED_LIMITS_BEGIN_TIME = "alt-speed-time-begin"
private const val ALTERNATIVE_SPEED_LIMITS_END_TIME = "alt-speed-time-end"
private const val ALTERNATIVE_SPEED_LIMITS_DAYS = "alt-speed-time-day"

private const val PEER_PORT = "peer-port"
private const val RANDOM_PORT_ENABLED = "peer-port-random-on-start"
private const val PORT_FORWARDING_ENABLED = "port-forwarding-enabled"
private const val ENCRYPTION = "encryption"
private const val UTP_ENABLED = "utp-enabled"
private const val PEX_ENABLED = "pex-enabled"
private const val DHT_ENABLED = "dht-enabled"
private const val LPD_ENABLED = "lpd-enabled"
private const val PEERS_LIMIT_PER_TORRENT = "peer-limit-per-torrent"
private const val PEERS_LIMIT_GLOBAL = "peer-limit-global"

class ServerSettings {
    object Days {
        const val SUNDAY = (1 shl 0)
        const val MONDAY = (1 shl 1)
        const val TUESDAY = (1 shl 2)
        const val WEDNESDAY = (1 shl 3)
        const val THURSDAY = (1 shl 4)
        const val FRIDAY = (1 shl 5)
        const val SATURDAY = (1 shl 6)
        const val WEEKDAYS = (MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY)
        const val WEEKENDS = (SUNDAY or SATURDAY)
        const val ALL = (WEEKDAYS or WEEKENDS)
    }

    object Encryption {
        const val ALLOWED = "tolerated"
        const val PREFERRED = "preferred"
        const val REQUIRED = "required"
    }

    var rpcVersion = 0
        private set
    var minimumRpcVersion = 0
        private set

    val canRenameFiles: Boolean
        get() {
            return (rpcVersion >= 15)
        }

    var downloadDirectory by Delegate(String(), DOWNLOAD_DIRECTORY)
    var startAddedTorrents by Delegate(false, START_ADDED_TORRENTS)
    var renameIncompleteFiles by Delegate(false, RENAME_INCOMPLETE_FILES)
    var incompleteFilesDirectoryEnabled by Delegate(false, INCOMPLETE_FILES_DIRECTORY_ENABLED)
    var incompleteFilesDirectory by Delegate(String(), INCOMPLETE_FILES_DIRECTORY)

    var ratioLimited by Delegate(false, RATIO_LIMITED)
    var ratioLimit by Delegate(0.0, RATIO_LIMIT)
    var idleSeedingLimited by Delegate(false, IDLE_SEEDING_LIMITED)
    var idleSeedingLimit by Delegate(0, IDLE_SEEDING_LIMIT)

    var downloadQueueEnabled by Delegate(false, DOWNLOAD_QUEUE_ENABLED)
    var downloadQueueSize by Delegate(0, DOWNLOAD_QUEUE_SIZE)
    var seedQueueEnabled by Delegate(false, SEED_QUEUE_ENABLED)
    var seedQueueSize by Delegate(0, SEED_QUEUE_SIZE)
    var idleQueueLimited by Delegate(false, IDLE_QUEUE_LIMITED)
    var idleQueueLimit by Delegate(0, IDLE_QUEUE_LIMIT)

    var downloadSpeedLimited by Delegate(false, DOWNLOAD_SPEED_LIMITED)
    var downloadSpeedLimit by Delegate(0, DOWNLOAD_SPEED_LIMIT)
    var uploadSpeedLimited by Delegate(false, UPLOAD_SPEED_LIMITED)
    var uploadSpeedLimit by Delegate(0, UPLOAD_SPEED_LIMIT)
    var alternativeSpeedLimitsEnabled by Delegate(false, ALTERNATIVE_SPEED_LIMITS_ENABLED)
    var alternativeDownloadSpeedLimit by Delegate(0, ALTERNATIVE_DOWNLOAD_SPEED_LIMIT)
    var alternativeUploadSpeedLimit by Delegate(0, ALTERNATIVE_UPLOAD_SPEED_LIMIT)
    var alternativeSpeedLimitScheduled by Delegate(false, ALTERNATIVE_SPEED_LIMITS_SCHEDULED)
    var alternativeSpeedLimitsBeginTime by Delegate(0, ALTERNATIVE_SPEED_LIMITS_BEGIN_TIME)
    var alternativeSpeedLimitsEndTime by Delegate(0, ALTERNATIVE_SPEED_LIMITS_END_TIME)
    var alternativeSpeedLimitsDays by Delegate(Days.ALL, ALTERNATIVE_SPEED_LIMITS_DAYS)

    var peerPort by Delegate(0, PEER_PORT)
    var randomPortEnabled by Delegate(false, RANDOM_PORT_ENABLED)
    var portForwardingEnabled by Delegate(false, PORT_FORWARDING_ENABLED)
    var encryption by Delegate(Encryption.ALLOWED, ENCRYPTION)
    var utpEnabled by Delegate(false, UTP_ENABLED)
    var pexEnabled by Delegate(false, PEX_ENABLED)
    var dhtEnabled by Delegate(false, DHT_ENABLED)
    var lpdEnabled by Delegate(false, LPD_ENABLED)
    var peersLimitPerTorrent by Delegate(0, PEERS_LIMIT_PER_TORRENT)
    var peersLimitGlobal by Delegate(0, PEERS_LIMIT_GLOBAL)

    private var updating = false

    fun update(settings: JsonObject) {
        updating = true

        rpcVersion = settings["rpc-version"].asInt
        minimumRpcVersion = settings["rpc-version-minimum"].asInt

        downloadDirectory = settings[DOWNLOAD_DIRECTORY].asString
        startAddedTorrents = settings[START_ADDED_TORRENTS].asBoolean
        renameIncompleteFiles = settings[RENAME_INCOMPLETE_FILES].asBoolean
        incompleteFilesDirectoryEnabled = settings[INCOMPLETE_FILES_DIRECTORY_ENABLED].asBoolean
        incompleteFilesDirectory = settings[INCOMPLETE_FILES_DIRECTORY].asString

        ratioLimited = settings[RATIO_LIMITED].asBoolean
        ratioLimit = settings[RATIO_LIMIT].asDouble
        idleSeedingLimited = settings[IDLE_SEEDING_LIMITED].asBoolean
        idleSeedingLimit = settings[IDLE_SEEDING_LIMIT].asInt

        downloadQueueEnabled = settings[DOWNLOAD_QUEUE_ENABLED].asBoolean
        downloadQueueSize = settings[DOWNLOAD_QUEUE_SIZE].asInt
        seedQueueEnabled = settings[SEED_QUEUE_ENABLED].asBoolean
        seedQueueSize = settings[SEED_QUEUE_SIZE].asInt
        idleQueueLimited = settings[IDLE_QUEUE_LIMITED].asBoolean
        idleQueueLimit = settings[IDLE_QUEUE_LIMIT].asInt

        downloadSpeedLimited = settings[DOWNLOAD_SPEED_LIMITED].asBoolean
        downloadSpeedLimit = settings[DOWNLOAD_SPEED_LIMIT].asInt
        uploadSpeedLimited = settings[UPLOAD_SPEED_LIMITED].asBoolean
        uploadSpeedLimit = settings[UPLOAD_SPEED_LIMIT].asInt
        alternativeSpeedLimitsEnabled = settings[ALTERNATIVE_SPEED_LIMITS_ENABLED].asBoolean
        alternativeDownloadSpeedLimit = settings[ALTERNATIVE_DOWNLOAD_SPEED_LIMIT].asInt
        alternativeUploadSpeedLimit = settings[ALTERNATIVE_UPLOAD_SPEED_LIMIT].asInt
        alternativeSpeedLimitScheduled = settings[ALTERNATIVE_SPEED_LIMITS_SCHEDULED].asBoolean
        alternativeSpeedLimitsBeginTime = settings[ALTERNATIVE_SPEED_LIMITS_BEGIN_TIME].asInt
        alternativeSpeedLimitsEndTime = settings[ALTERNATIVE_SPEED_LIMITS_END_TIME].asInt
        alternativeSpeedLimitsDays = settings[ALTERNATIVE_SPEED_LIMITS_DAYS].asInt

        peerPort = settings[PEER_PORT].asInt
        randomPortEnabled = settings[RANDOM_PORT_ENABLED].asBoolean
        portForwardingEnabled = settings[PORT_FORWARDING_ENABLED].asBoolean
        encryption = settings[ENCRYPTION].asString
        utpEnabled = settings[UTP_ENABLED].asBoolean
        pexEnabled = settings[PEX_ENABLED].asBoolean
        dhtEnabled = settings[DHT_ENABLED].asBoolean
        lpdEnabled = settings[LPD_ENABLED].asBoolean
        peersLimitPerTorrent = settings[PEERS_LIMIT_PER_TORRENT].asInt
        peersLimitGlobal = settings[PEERS_LIMIT_GLOBAL].asInt

        updating = false
    }

    private class Delegate<T : Any>(private var field: T, private val key: String) {
        operator fun getValue(thisRef: ServerSettings, property: KProperty<*>) = field

        operator fun setValue(thisRef: ServerSettings, property: KProperty<*>, value: T) {
            if (value != field) {
                field = value
                if (!thisRef.updating) {
                    Rpc.setSessionProperty(key, value)
                }
            }
        }
    }
}