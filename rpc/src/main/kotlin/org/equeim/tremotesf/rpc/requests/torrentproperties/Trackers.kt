// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

@file:UseSerializers(UnixTimeToInstantSerializer::class)

package org.equeim.tremotesf.rpc.requests.torrentproperties

import androidx.annotation.VisibleForTesting
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import org.equeim.tremotesf.rpc.requests.SingleTorrentRequestArguments
import org.equeim.tremotesf.rpc.requests.SingleTorrentResponseArguments
import org.equeim.tremotesf.rpc.requests.UnixTimeToInstantSerializer
import timber.log.Timber
import java.time.Instant

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentTrackers(hashString: String): List<Tracker>? =
    performRequest<RpcResponse<SingleTorrentResponseArguments<TorrentTrackers>>, _>(
        RpcMethod.TorrentGet,
        SingleTorrentRequestArguments(hashString, "trackerStats"),
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
private data class TorrentTrackers(@SerialName("trackerStats") val trackers: List<Tracker>)

private object PeersCountSerializer : KSerializer<Int> {
    override val descriptor =
        PrimitiveSerialDescriptor(PeersCountSerializer::class.qualifiedName!!, PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int = decoder.decodeInt().coerceAtLeast(0)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

suspend fun RpcClient.addTorrentTrackers(
    torrentHashString: String,
    trackersToAdd: List<Set<String>>,
    existingTrackersMaybe: List<Tracker>? = null
) {
    val capabilities = checkServerCapabilities(
        force = false,
        RpcRequestContext(RpcMethod.SessionSet, "addTorrentTrackers")
    )
    if (capabilities.hasTrackerListProperty) {
        addTorrentTrackersNewMethod(torrentHashString, trackersToAdd, existingTrackersMaybe)
    } else {
        addTorrentTrackersOldMethod(torrentHashString, trackersToAdd, existingTrackersMaybe)
    }
}

/**
 * @throws RpcRequestError
 */
private suspend fun RpcClient.addTorrentTrackersNewMethod(
    torrentHashString: String,
    trackersToAdd: List<Set<String>>,
    existingTrackersMaybe: List<Tracker>?,
) {
    val existingTrackers = existingTrackersMaybe?.toTieredAnnounceUrls() ?: getTorrentTieredTrackersAnnounceUrls(torrentHashString).orEmpty()
    Timber.d("Merging existing trackers $existingTrackers with $trackersToAdd")
    val merged = existingTrackers.mergeWith(trackersToAdd)
    Timber.d("Result is $merged")
    if (merged != existingTrackers) {
        Timber.d("Replacing trackers")
        setTieredTrackerList(torrentHashString, merged)
    } else {
        Timber.d("Result is the same, do nothing")
    }
}

/**
 * @throws RpcRequestError
 */
private suspend fun RpcClient.getTorrentTieredTrackersAnnounceUrls(hashString: String): List<Set<String>>? =
    performRequest<RpcResponse<SingleTorrentResponseArguments<TorrentTieredTrackersAnnounceUrls>>, _>(
        RpcMethod.TorrentGet,
        SingleTorrentRequestArguments(hashString, "trackerList"),
        "getTorrentTieredTrackersAnnounceUrls"
    ).arguments.torrents.firstOrNull()?.trackers

@Serializable
private data class TorrentTieredTrackersAnnounceUrls(
    @Serializable(TieredTrackersAnnounceUrlsSerializer::class)
    @SerialName("trackerList")
    val trackers: List<Set<String>>
)

/**
 * @throws RpcRequestError
 */
private suspend fun RpcClient.addTorrentTrackersOldMethod(
    torrentHashString: String,
    trackersToAddTiered: List<Set<String>>,
    existingTrackersMaybe: List<Tracker>?,
) {
    // Transmission adds each announce URL to each own tier when using trackerAdd property, so take first URL from each tier
    val existingTrackers = (existingTrackersMaybe ?: getTorrentTrackers(torrentHashString).orEmpty())
        .toTieredAnnounceUrls()
        .firstFromTiers()
        .toSet()
    val trackersToAdd = trackersToAddTiered.firstFromTiers()
    Timber.d("Merging existing trackers $existingTrackers with $trackersToAdd")
    val trackersToAddFiltered = trackersToAdd - existingTrackers
    if (trackersToAddFiltered.isNotEmpty()) {
        Timber.d("Adding trackers $trackersToAddFiltered")
        setTorrentProperty(torrentHashString, "trackerAdd", trackersToAddFiltered)
    } else {
        Timber.d("Nothing to add")
    }
}

private fun List<Set<String>>.firstFromTiers(): List<String> = mapNotNull { it.firstOrNull() }

suspend fun RpcClient.replaceTorrentTracker(
    torrentHashString: String,
    trackerId: Int,
    newAnnounceUrl: String,
    allTrackers: List<Tracker>
) {
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

suspend fun RpcClient.removeTorrentTrackers(
    torrentHashString: String,
    trackerIds: List<Int>,
    allTrackers: List<Tracker>
) {
    val capabilities = checkServerCapabilities(
        force = false,
        RpcRequestContext(RpcMethod.SessionSet, "replaceTorrentTracker")
    )
    if (capabilities.hasTrackerListProperty) {
        setTieredTrackerList(
            torrentHashString,
            allTrackers.asSequence().filterNot { trackerIds.contains(it.id) }.toTieredAnnounceUrls()
        )
    } else {
        setTorrentProperty(torrentHashString, "trackerRemove", trackerIds)
    }
}

private suspend fun RpcClient.setTieredTrackerList(
    torrentHashString: String,
    tieredAnnounceUrls: List<Set<String>>
) =
    setTorrentProperty(
        torrentHashString,
        "trackerList",
        tieredAnnounceUrls,
        TieredTrackersAnnounceUrlsSerializer
    )

private fun Sequence<Tracker>.toTieredAnnounceUrls(): List<Set<String>> {
    return groupingBy(Tracker::tier).fold(mutableSetOf<String>()) { tier, tracker ->
        tier.add(
            tracker.announceUrl
        ); tier
    }.values.toList()
}

private fun Iterable<Tracker>.toTieredAnnounceUrls(): List<Set<String>> =
    asSequence().toTieredAnnounceUrls()

@VisibleForTesting
internal fun List<Set<String>>.mergeWith(newTrackers: List<Set<String>>): List<Set<String>> {
    Timber.d("Merging $this with $newTrackers")
    val merged = MutableList(size) { get(it).toMutableSet() }
    for (newTier in newTrackers) {
        val existingTier = merged.find { tier -> newTier.any(tier::contains) }
        if (existingTier != null) {
            existingTier.addAll(newTier)
        } else {
            merged.add(newTier.toMutableSet())
        }
    }
    return merged
}

@VisibleForTesting
internal object TieredTrackersAnnounceUrlsSerializer : KSerializer<List<Set<String>>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        TieredTrackersAnnounceUrlsSerializer::class.qualifiedName!!,
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): List<Set<String>> {
        val tiers = mutableListOf<Set<String>>()
        var lastTier: MutableSet<String>? = null
        for (line in decoder.decodeString().lineSequence()) {
            if (line.isNotEmpty()) {
                if (lastTier == null) {
                    lastTier = mutableSetOf()
                    tiers.add(lastTier)
                }
                lastTier.add(line)
            } else {
                lastTier = null
            }
        }
        return tiers
    }

    override fun serialize(encoder: Encoder, value: List<Set<String>>) {
        encoder.encodeString(buildString {
            for ((index, tier) in value.withIndex()) {
                if (index > 0) {
                    append("\n\n")
                }
                tier.joinTo(
                    buffer = this,
                    separator = "\n",
                )
            }
        })
    }
}
