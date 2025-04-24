// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestContext
import org.equeim.tremotesf.rpc.RpcRequestError
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getTorrentsList(): List<Torrent> = if (
    checkServerCapabilities(force = false, RpcRequestContext(RpcMethod.TorrentGet, "getTorrentsList")).hasTableMode) {
    performRequest<RpcResponse<TorrentsListTableResponseArguments>>(
        TORRENTS_LIST_TABLE_REQUEST,
        "getTorrentsList"
    ).arguments.torrents
} else {
    performRequest<RpcResponse<TorrentsListObjectsResponseArguments>>(
        TORRENTS_LIST_OBJECTS_REQUEST,
        "getTorrentsList"
    ).arguments.torrents
}

private val FIELDS = Torrent.serializer().descriptor.elementNames.toList()

@Serializable
private data class TorrentsListObjectsRequestArguments(
    @SerialName("fields")
    val fields: List<String> = FIELDS,
)

private val TORRENTS_LIST_OBJECTS_REQUEST = RpcRequestBody(RpcMethod.TorrentGet, TorrentsListObjectsRequestArguments())

@Serializable
private data class TorrentsListObjectsResponseArguments(
    @SerialName("torrents")
    val torrents: List<Torrent>,
)

@Serializable
private data class TorrentsListTableRequestArguments(
    @SerialName("fields")
    val fields: List<String> = FIELDS,
    @SerialName("format")
    val format: String = "table",
)

private val TORRENTS_LIST_TABLE_REQUEST = RpcRequestBody(RpcMethod.TorrentGet, TorrentsListTableRequestArguments())

@Serializable
private data class TorrentsListTableResponseArguments(
    @Serializable(TorrentsListTableSerializer::class)
    @SerialName("torrents")
    val torrents: List<Torrent>,
)

private object TorrentsListTableSerializer : TorrentsTableSerializer<Torrent>(serializer())

@Serializable
data class Torrent(
    @SerialName("id")
    val id: Int,
    @SerialName("hashString")
    override val hashString: String,
    @SerialName("magnetLink")
    val magnetLink: String,
    @SerialName("name")
    override val name: String,
    @SerialName("status")
    val status: TorrentStatus,
    @SerialName("error")
    val error: Error?,
    @SerialName("errorString")
    val errorString: String,
    @Contextual
    @SerialName("downloadDir")
    val downloadDirectory: NormalizedRpcPath,
    @SerialName("percentDone")
    val percentDone: Double,
    @SerialName("recheckProgress")
    val recheckProgress: Double,
    @Serializable(OptionalSecondsToDurationSerializer::class)
    @SerialName("eta")
    val eta: Duration?,
    @SerialName("uploadRatio")
    val ratio: Double,
    @SerialName("totalSize")
    val totalSize: FileSize,
    @SerialName("haveValid")
    val completedSize: FileSize,
    @SerialName("leftUntilDone")
    val leftUntilDone: FileSize,
    @SerialName("sizeWhenDone")
    override val sizeWhenDone: FileSize,
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
    @Serializable(UnixTimeToInstantSerializer::class)
    @SerialName("addedDate")
    val addedDate: Instant?,
    @Serializable(TrackerSitesSerializer::class)
    @SerialName("trackers")
    val trackerSites: List<String>,
) : RpcTorrentFinishedState {
    override val isFinished: Boolean get() = leftUntilDone.bytes == 0L
    val isDownloadingStalled: Boolean get() = peersSendingToUsCount == 0 && webSeedersSendingToUsCount == 0
    val isSeedingStalled: Boolean get() = peersGettingFromUsCount == 0

    @Serializable(Error.Serializer::class)
    enum class Error(override val rpcValue: Int) : RpcEnum {
        TrackerWarning(1),
        TrackerError(2),
        LocalError(3);

        internal object Serializer : KSerializer<Error?> {
            private object Delegate : RpcEnum.Serializer<Error>(Error::class)

            private const val Null = 0

            override val descriptor: SerialDescriptor get() = Delegate.descriptor

            override fun deserialize(decoder: Decoder): Error? {
                val rpcValue = decoder.decodeInt()
                return if (rpcValue == Null) null else Delegate.deserialize(rpcValue)
            }

            override fun serialize(encoder: Encoder, value: Error?) {
                if (value == null) {
                    encoder.encodeInt(Null)
                } else {
                    Delegate.serialize(encoder, value)
                }
            }
        }
    }
}

private object TrackerSitesSerializer : JsonTransformingSerializer<List<String>>(serializer()) {
    private val cache = ConcurrentHashMap<JsonElement, JsonElement>()

    override fun transformDeserialize(element: JsonElement): JsonElement {
        try {
            return JsonArray(element.jsonArray.map { arrayElement ->
                val announceUrl = arrayElement.jsonObject.getValue("announce").jsonPrimitive
                cache.getOrPut(announceUrl) {
                    // We can't convert announceUrl to HttpUrl directly since announce URLs may have udp:// scheme
                    // and HttpUrl supports only http:// and https://
                    // Extract host using URI and construct fake HttpUrl using http:// scheme instead
                    // android.net.Uri incorrectly parses URL wit IPv6 addresses on older Android versions,
                    // so we use java.net.URI which works
                    val uri = try {
                        URI(announceUrl.content)
                    } catch (e: URISyntaxException) {
                        Timber.e(e, "Failed to parse URI from ${announceUrl.content}")
                        null
                    }
                    uri?.host?.let { host ->
                        JsonPrimitive(HttpUrl.Builder().scheme("http").host(host).build().topPrivateDomain() ?: host)
                    } ?: announceUrl
                }
            })
        } catch (e: Exception) {
            throw SerializationException(e)
        }
    }
}

internal abstract class TorrentsTableSerializer<TorrentT : Any>(serializer: KSerializer<List<TorrentT>>) :
    JsonTransformingSerializer<List<TorrentT>>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val table = element as? JsonArray ?: throw SerializationException()
        val fields = ((table.firstOrNull() as? JsonArray) ?: throw SerializationException()).map { field ->
            if (field !is JsonPrimitive) throw SerializationException()
            if (!field.isString) throw SerializationException()
            field.content
        }
        return JsonArray(table.asSequence().drop(1).map { values ->
            values as? JsonArray ?: throw SerializationException()
            JsonObject(values.withIndex().associateBy(
                keySelector = { fields[it.index] },
                valueTransform = { it.value }
            ))
        }.toList())
    }
}
