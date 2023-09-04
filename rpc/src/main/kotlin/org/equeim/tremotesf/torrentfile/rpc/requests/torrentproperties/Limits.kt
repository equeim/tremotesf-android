package org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.MinutesToDurationSerializer
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcEnum
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcResponse
import org.equeim.tremotesf.torrentfile.rpc.requests.TransferRate
import kotlin.time.Duration

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentLimits(hashString: String): TorrentLimits? =
    performRequest<RpcResponse<TorrentLimitsResponseArguments>, _>(
        RpcMethod.TorrentGet,
        TorrentLimitsRequestArguments(hashString),
        "getTorrentLimits"
    ).arguments.torrents.firstOrNull()

@Serializable
private data class TorrentLimitsRequestArguments(
    @SerialName("ids")
    val ids: List<String>,

    @SerialName("fields")
    @OptIn(ExperimentalSerializationApi::class)
    val fields: List<String> = TorrentLimits.serializer().descriptor.elementNames.toList(),
) {
    constructor(hashString: String) : this(ids = listOf(hashString))
}

@Serializable
private data class TorrentLimitsResponseArguments(
    @SerialName("torrents")
    val torrents: List<TorrentLimits>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }
}

@Serializable
data class TorrentLimits(
    @SerialName("id")
    val id: Int,

    @SerialName("honorsSessionLimits")
    val honorsSessionLimits: Boolean,
    @SerialName("bandwidthPriority")
    val bandwidthPriority: BandwidthPriority,
    @SerialName("downloadLimited")
    val downloadSpeedLimited: Boolean,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("downloadLimit")
    val downloadSpeedLimit: TransferRate,
    @SerialName("uploadLimited")
    val uploadSpeedLimited: Boolean,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("uploadLimit")
    val uploadSpeedLimit: TransferRate,

    @SerialName("seedRatioLimit")
    val ratioLimit: Double,
    @SerialName("seedRatioMode")
    val ratioLimitMode: RatioLimitMode,

    @Serializable(MinutesToDurationSerializer::class)
    @SerialName("seedIdleLimit")
    val idleSeedingLimit: Duration,
    @SerialName("seedIdleMode")
    val idleSeedingLimitMode: IdleSeedingLimitMode,

    @SerialName("peer-limit")
    val peersLimit: Int,
) {

    @Serializable(BandwidthPriority.Serializer::class)
    enum class BandwidthPriority(override val rpcValue: Int) : RpcEnum {
        Low(-1),
        Normal(0),
        High(1);

        internal object Serializer : RpcEnum.Serializer<BandwidthPriority>(BandwidthPriority::class)
    }

    @Serializable(RatioLimitMode.Serializer::class)
    enum class RatioLimitMode(override val rpcValue: Int) : RpcEnum {
        Global(0),
        Single(1),
        Unlimited(2);

        internal object Serializer : RpcEnum.Serializer<RatioLimitMode>(RatioLimitMode::class)
    }

    @Serializable(IdleSeedingLimitMode.Serializer::class)
    enum class IdleSeedingLimitMode(override val rpcValue: Int) : RpcEnum {
        Global(0),
        Single(1),
        Unlimited(2);

        internal object Serializer : RpcEnum.Serializer<IdleSeedingLimitMode>(IdleSeedingLimitMode::class)
    }
}
