// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcResponseWithoutArguments
import org.equeim.tremotesf.rpc.requests.TransferRate
import kotlin.time.Duration

suspend fun RpcClient.setTorrentHonorSessionLimits(torrentHashString: String, value: Boolean) =
    setTorrentProperty(torrentHashString, "honorsSessionLimits", value)

suspend fun RpcClient.setTorrentBandwidthPriority(torrentHashString: String, value: TorrentLimits.BandwidthPriority) =
    setTorrentProperty(torrentHashString, "bandwidthPriority", value)

suspend fun RpcClient.setTorrentDownloadSpeedLimit(torrentHashString: String, value: TransferRate) =
    setTorrentProperty(torrentHashString, "downloadLimit", value, org.equeim.tremotesf.rpc.requests.TransferRate.KiloBytesPerSecondSerializer)

suspend fun RpcClient.setTorrentDownloadSpeedLimited(torrentHashString: String, value: Boolean) =
    setTorrentProperty(torrentHashString, "downloadLimited", value)

suspend fun RpcClient.setTorrentUploadSpeedLimit(torrentHashString: String, value: TransferRate) =
    setTorrentProperty(torrentHashString, "uploadLimit", value, org.equeim.tremotesf.rpc.requests.TransferRate.KiloBytesPerSecondSerializer)

suspend fun RpcClient.setTorrentUploadSpeedLimited(torrentHashString: String, value: Boolean) =
    setTorrentProperty(torrentHashString, "uploadLimited", value)

suspend fun RpcClient.setTorrentRatioLimit(torrentHashString: String, value: Double) =
    setTorrentProperty(torrentHashString, "seedRatioLimit", value)

suspend fun RpcClient.setTorrentRatioLimitMode(torrentHashString: String, value: TorrentLimits.RatioLimitMode) =
    setTorrentProperty(torrentHashString, "seedRatioMode", value)

suspend fun RpcClient.setTorrentIdleSeedingLimit(torrentHashString: String, value: Duration) =
    setTorrentProperty(torrentHashString, "seedIdleLimit", value,
        org.equeim.tremotesf.rpc.requests.MinutesToDurationSerializer
    )

suspend fun RpcClient.setTorrentIdleSeedingLimitMode(
    torrentHashString: String,
    value: TorrentLimits.IdleSeedingLimitMode,
) =
    setTorrentProperty(torrentHashString, "seedIdleMode", value)

suspend fun RpcClient.setTorrentPeersLimit(torrentHashString: String, value: Int) =
    setTorrentProperty(torrentHashString, "peer-limit", value)

suspend fun RpcClient.setTorrentFilesWanted(torrentHashString: String, fileIndices: List<Int>, wanted: Boolean) =
    setTorrentProperty(torrentHashString, if (wanted) "files-wanted" else "files-unwanted", fileIndices)

suspend fun RpcClient.setTorrentFilesPriority(
    torrentHashString: String,
    fileIndices: List<Int>,
    priority: TorrentFiles.FilePriority,
) =
    setTorrentProperty(
        torrentHashString, when (priority) {
            org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentFiles.FilePriority.Low -> "priority-low"
            org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentFiles.FilePriority.Normal -> "priority-normal"
            org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentFiles.FilePriority.High -> "priority-high"
        }, fileIndices
    )

suspend fun RpcClient.addTorrentTrackers(torrentHashString: String, trackerAnnounceUrls: List<String>) =
    setTorrentProperty(torrentHashString, "trackerAdd", trackerAnnounceUrls)

suspend fun RpcClient.replaceTorrentTracker(torrentHashString: String, trackerId: Int, newAnnounceUrl: String) =
    setTorrentProperty(torrentHashString, "trackerReplace", buildJsonArray {
        add(JsonPrimitive(trackerId))
        add(JsonPrimitive(newAnnounceUrl))
    })

suspend fun RpcClient.removeTorrentTrackers(torrentHashString: String, trackerIds: List<Int>) =
    setTorrentProperty(torrentHashString, "trackerRemove", trackerIds)

/**
 * @throws RpcRequestError
 */
private suspend inline fun <reified T> RpcClient.setTorrentProperty(
    torrentHashString: String,
    property: String,
    value: T,
): Unit =
    setTorrentProperty(torrentHashString, property, value, serializer())

/**
 * @throws RpcRequestError
 */
private suspend fun <T> RpcClient.setTorrentProperty(
    torrentHashString: String,
    property: String,
    value: T,
    serializer: KSerializer<T>,
) {
    performRequest<RpcResponseWithoutArguments, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentSet,
        buildJsonObject {
            put("ids", JsonArray(listOf(JsonPrimitive(torrentHashString))))
            put(property, json.encodeToJsonElement(serializer, value))
        },
        property
    )
}
