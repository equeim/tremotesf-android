// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

@file:UseSerializers(UnixTimeToInstantSerializer::class)

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.requests.OptionalSecondsToDurationSerializer
import org.equeim.tremotesf.rpc.requests.RpcResponse
import org.equeim.tremotesf.rpc.requests.TorrentStatus
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.UnixTimeToInstantSerializer
import org.threeten.bp.Instant
import kotlin.time.Duration

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentDetails(hashString: String): TorrentDetails? =
    performRequest<RpcResponse<TorrentDetailsResponseArguments>, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentGet,
        TorrentDetailsRequestArguments(hashString),
        "getTorrentDetails"
    ).arguments.torrents.firstOrNull()

@Serializable
private data class TorrentDetailsRequestArguments(
    @SerialName("ids")
    val ids: List<String>,

    @SerialName("fields")
    @OptIn(ExperimentalSerializationApi::class)
    val fields: List<String> = TorrentDetails.serializer().descriptor.elementNames.toList(),
) {
    constructor(hashString: String) : this(ids = listOf(hashString))
}

@Serializable
private data class TorrentDetailsResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentDetails>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }
}

@Serializable
data class TorrentDetails(
    @SerialName("id")
    val id: Int,
    @SerialName("hashString")
    val hashString: String,
    @SerialName("magnetLink")
    val magnetLink: String,
    @SerialName("name")
    val name: String,
    @SerialName("status")
    val status: TorrentStatus,
    @Contextual
    @SerialName("downloadDir")
    val downloadDirectory: NormalizedRpcPath,
    @Serializable(OptionalSecondsToDurationSerializer::class)
    @SerialName("eta")
    val eta: Duration?,
    @SerialName("uploadRatio")
    val ratio: Double,
    @SerialName("totalSize")
    val totalSize: FileSize,
    @SerialName("haveValid")
    val completedSize: FileSize,
    @SerialName("uploadedEver")
    val totalUploaded: FileSize,
    @SerialName("rateDownload")
    val downloadSpeed: TransferRate,
    @SerialName("rateUpload")
    val uploadSpeed: TransferRate,
    @SerialName("peersSendingToUs")
    val peersSendingToUsCount: Int,
    @SerialName("peersGettingFromUs")
    val peersGettingFromUsCount: Int,
    @SerialName("webseedsSendingToUs")
    val webSeedersSendingToUsCount: Int,
    @SerialName("addedDate")
    val addedDate: Instant?,

    @SerialName("downloadedEver")
    val totalDownloaded: FileSize,
    @SerialName("activityDate")
    val activityDate: Instant?,
    @SerialName("dateCreated")
    val creationDate: Instant?,
    @SerialName("comment")
    val comment: String,
    @SerialName("creator")
    val creator: String,

    @SerialName("trackerStats")
    val trackerStats: List<TrackerStats>,
) {
    val totalSeedersFromTrackers: Int = trackerStats.fold(0) { count, tracker ->
        if (tracker.seeders > 0) count + tracker.seeders else count
    }
    val totalLeechersFromTrackers: Int = trackerStats.fold(0) { count, tracker ->
        if (tracker.leechers > 0) count + tracker.leechers else count
    }

    @Serializable
    data class TrackerStats(
        @SerialName("seederCount")
        val seeders: Int,
        @SerialName("leecherCount")
        val leechers: Int,
    )
}
