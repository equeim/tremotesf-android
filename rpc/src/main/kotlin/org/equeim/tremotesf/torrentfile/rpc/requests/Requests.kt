// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

@Serializable
internal data class RpcRequest<Arguments>(
    @SerialName("method")
    val method: RpcMethod,
    @SerialName("arguments")
    val arguments: Arguments,
)

@Serializable
internal data class RpcRequestWithoutArguments(
    @SerialName("method")
    val method: RpcMethod,
)

@Serializable
internal enum class RpcMethod {
    @SerialName("torrent-set")
    TorrentSet,

    @SerialName("torrent-get")
    TorrentGet,

    @SerialName("torrent-add")
    TorrentAdd,

    @SerialName("torrent-set-location")
    TorrentSetLocation,

    @SerialName("torrent-start")
    TorrentStart,

    @SerialName("torrent-start-now")
    TorrentStartNow,

    @SerialName("torrent-stop")
    TorrentStop,

    @SerialName("torrent-verify")
    TorrentVerify,

    @SerialName("torrent-reannounce")
    TorrentReannounce,

    @SerialName("torrent-rename-path")
    TorrentRenamePath,

    @SerialName("torrent-remove")
    TorrentRemove,

    @SerialName("session-get")
    SessionGet,

    @SerialName("session-set")
    SessionSet,

    @SerialName("session-stats")
    SessionStats,

    @SerialName("free-space")
    FreeSpace;

    @OptIn(ExperimentalSerializationApi::class)
    override fun toString(): String = serializer().descriptor.getElementName(ordinal)
}

@Serializable
internal data class RequestWithIds(
    @SerialName("ids")
    val ids: List<Int>,
)

internal interface BaseRpcResponse {
    val result: String
    var httpResponse: Response
}

internal val BaseRpcResponse.isSuccessful: Boolean get() = result == SUCCESS_RESULT

@Serializable(RpcResponse.Serializer::class)
internal data class RpcResponse<Arguments : Any>(
    override val result: String,
    private val _arguments: Arguments?,
) : BaseRpcResponse {
    val arguments: Arguments get() = checkNotNull(_arguments)

    @Transient
    override lateinit var httpResponse: Response

    class Serializer<Arguments : Any>(private val argumentsSerializer: KSerializer<Arguments>) : KSerializer<RpcResponse<Arguments>> {
        private val delegate = JsonObject.serializer()
        override val descriptor: SerialDescriptor get() = delegate.descriptor

        override fun deserialize(decoder: Decoder): RpcResponse<Arguments> = try {
            val response = decoder.decodeSerializableValue(delegate)
            val result = response.getValue("result").jsonPrimitive.content
            if (result == SUCCESS_RESULT) {
                val arguments = response.getValue("arguments")
                RpcResponse(
                    result,
                    (decoder as JsonDecoder).json.decodeFromJsonElement(argumentsSerializer, arguments)
                )
            } else {
                RpcResponse(result, null)
            }
        } catch (e: SerializationException) {
            throw e
        } catch (e: Exception) {
            throw SerializationException(e)
        }

        override fun serialize(encoder: Encoder, value: RpcResponse<Arguments>) = throw NotImplementedError()
    }
}

@Serializable
internal data class RpcResponseWithoutArguments(
    @SerialName("result")
    override val result: String,
) : BaseRpcResponse {
    @Transient
    override lateinit var httpResponse: Response
}

private const val SUCCESS_RESULT = "success"