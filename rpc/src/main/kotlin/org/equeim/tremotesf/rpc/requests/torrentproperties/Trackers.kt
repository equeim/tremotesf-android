// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

@file:UseSerializers(UnixTimeToInstantSerializer::class)

package org.equeim.tremotesf.rpc.requests.torrentproperties

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestContext
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.RpcEnum
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.RpcResponse
import org.equeim.tremotesf.rpc.requests.UnixTimeToInstantSerializer
import java.time.Instant

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentTrackers(hashString: String): List<Tracker>? =
    performRequest<RpcResponse<TorrentTrackersResponseArguments>, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentGet,
        TorrentTrackersRequestArguments(hashString),
        "getTorrentTrackers"
    ).arguments.torrents.firstOrNull()?.trackers

@Serializable
data class Tracker(
    @SerialName("id")
    val id: Int,
    @SerialName("announce")
    val announceUrl: String,
    @SerialName("announceState")
    val status: Status,
    @SerialName("lastAnnounceSucceeded")
    val lastAnnounceSucceeded: Boolean,
    @SerialName("lastAnnounceTime")
    val lastAnnounceTime: Instant?,
    @SerialName("lastAnnounceResult")
    val lastAnnounceResult: String,
    @Serializable(PeersCountSerializer::class)
    @SerialName("lastAnnouncePeerCount")
    val peers: Int,
    @Serializable(PeersCountSerializer::class)
    @SerialName("seederCount")
    val seeders: Int,
    @Serializable(PeersCountSerializer::class)
    @SerialName("leecherCount")
    val leechers: Int,
    @SerialName("nextAnnounceTime")
    val nextUpdateTime: Instant?,
    @SerialName("tier")
    val tier: Int,
) {
    val errorMessage: String?
        get() = if (!lastAnnounceSucceeded && lastAnnounceTime != null) lastAnnounceResult else null

    @Serializable(Status.Serializer::class)
    enum class Status(override val rpcValue: Int) : RpcEnum {
        Inactive(0),
        WaitingForUpdate(1),
        QueuedForUpdate(2),
        Updating(3);

        internal object Serializer : RpcEnum.Serializer<Status>(Status::class)
    }
}

@Serializable
private data class TorrentTrackersRequestArguments(
    @SerialName("ids")
    val ids: List<String>,

    @SerialName("fields")
    val fields: List<String> = listOf("trackerStats"),
) {
    constructor(hashString: String) : this(ids = listOf(hashString))
}

@Serializable
private data class TorrentTrackersResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentFields>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }

    @Serializable
    data class TorrentFields(@SerialName("trackerStats") val trackers: List<Tracker>)
}

private object PeersCountSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor(PeersCountSerializer::class.qualifiedName!!, PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.decodeInt().coerceAtLeast(0)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

suspend fun RpcClient.addTorrentTrackers(torrentHashString: String, trackerAnnounceUrls: List<String>, allTrackers: List<Tracker>) {
    val capabilities = checkServerCapabilities(
        force = false,
        RpcRequestContext(RpcMethod.SessionSet, "addTorrentTrackers")
    )
    if (capabilities.hasTrackerListProperty) {
        val tieredAnnounceUrls = allTrackers.asSequence().toTieredAnnounceUrls()
        for (tracker in trackerAnnounceUrls) {
            tieredAnnounceUrls.add(mutableListOf(tracker))
        }
        setTieredTrackerList(torrentHashString, tieredAnnounceUrls)
    } else {
        setTorrentProperty(torrentHashString, "trackerAdd", trackerAnnounceUrls)
    }
}

suspend fun RpcClient.replaceTorrentTracker(torrentHashString: String, trackerId: Int, newAnnounceUrl: String, allTrackers: List<Tracker>) {
    val capabilities = checkServerCapabilities(
        force = false,
        RpcRequestContext(RpcMethod.SessionSet, "replaceTorrentTracker")
    )
    if (capabilities.hasTrackerListProperty) {
        val tieredAnnounceUrls = allTrackers.asSequence().map {
            if (it.id == trackerId) {
                it.copy(announceUrl = newAnnounceUrl)
            } else {
                it
            }
        }.toTieredAnnounceUrls()
        setTieredTrackerList(torrentHashString, tieredAnnounceUrls)
    } else {
        setTorrentProperty(torrentHashString, "trackerReplace", buildJsonArray {
            add(JsonPrimitive(trackerId))
            add(JsonPrimitive(newAnnounceUrl))
        })
    }
}

suspend fun RpcClient.removeTorrentTrackers(torrentHashString: String, trackerIds: List<Int>, allTrackers: List<Tracker>) {
    val capabilities = checkServerCapabilities(
        force = false,
        RpcRequestContext(RpcMethod.SessionSet, "replaceTorrentTracker")
    )
    if (capabilities.hasTrackerListProperty) {
        setTieredTrackerList(torrentHashString, allTrackers.asSequence().filterNot { trackerIds.contains(it.id) }.toTieredAnnounceUrls())
    } else {
        setTorrentProperty(torrentHashString, "trackerRemove", trackerIds)
    }
}

private suspend fun RpcClient.setTieredTrackerList(torrentHashString: String, tieredAnnounceUrls: List<List<String>>) =
    setTorrentProperty(torrentHashString, "trackerList", tieredAnnounceUrls, TieredTrackersAnnounceUrlsSerializer)

private fun Sequence<Tracker>.toTieredAnnounceUrls(): MutableList<List<String>> {
    val tieredAnnounceUrls = sortedMapOf<Int, MutableList<String>>()
    groupByTo(
        destination = tieredAnnounceUrls,
        keySelector = Tracker::tier,
        valueTransform = Tracker::announceUrl
    )
    return tieredAnnounceUrls.values.toMutableList()
}

private object TieredTrackersAnnounceUrlsSerializer : KSerializer<List<List<String>>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(TieredTrackersAnnounceUrlsSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<List<String>> {
        val tiers = mutableListOf<List<String>>()
        var lastTier: MutableList<String>? = null
        for (line in decoder.decodeString().lineSequence()) {
            if (line.isNotEmpty()) {
                if (lastTier == null) {
                    lastTier = mutableListOf()
                    tiers.add(lastTier)
                }
                lastTier.add(line)
            } else {
                lastTier = null
            }
        }
        return tiers
    }

    override fun serialize(encoder: Encoder, value: List<List<String>>) {
        encoder.encodeString(buildString {
            for ((index, announceUrls) in value.withIndex()) {
                if (index > 0) {
                    append("\n\n")
                }
                announceUrls.joinTo(
                    buffer = this,
                    separator = "\n",
                )
            }
        })
    }
}
