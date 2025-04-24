// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
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

    override fun toString(): String = serializer().descriptor.getElementName(ordinal)
}

@Serializable
internal data class RequestWithTorrentsIds(
    @SerialName("ids")
    val torrentIds: List<Int>,
)

internal interface BaseRpcResponse {
    val result: String
    val rawArguments: JsonObject?
    var httpResponse: Response
    var requestHeaders: Headers
}

internal val BaseRpcResponse.isSuccessful: Boolean get() = result == SUCCESS_RESULT

@Serializable(RpcResponse.Serializer::class)
internal data class RpcResponse<Arguments : Any>(
    override val result: String,
    override val rawArguments: JsonObject?,
    private val successArguments: Arguments?,
) : BaseRpcResponse {
    val arguments: Arguments get() = checkNotNull(successArguments)

    @Transient
    override lateinit var httpResponse: Response

    @Transient
    override lateinit var requestHeaders: Headers

    class Serializer<Arguments : Any>(private val argumentsSerializer: KSerializer<Arguments>) :
        KSerializer<RpcResponse<Arguments>> {
        private val delegate = JsonObject.serializer()
        override val descriptor: SerialDescriptor get() = delegate.descriptor

        override fun deserialize(decoder: Decoder): RpcResponse<Arguments> = try {
            val response = decoder.decodeSerializableValue(delegate)
            val result = response.getOrElse("result") {
                throw SerializationException("Missing \"result\" key in the response")
            }.jsonPrimitive.content
            val arguments = response["arguments"]?.jsonObject
            if (result == SUCCESS_RESULT) {
                if (arguments == null) throw SerializationException("Missing \"arguments\" key in the response")
                RpcResponse(
                    result = result,
                    rawArguments = arguments,
                    successArguments = (decoder as JsonDecoder).json.decodeFromJsonElement(
                        argumentsSerializer,
                        arguments
                    ),
                )
            } else {
                RpcResponse(result = result, rawArguments = arguments, successArguments = null)
            }
        } catch (e: SerializationException) {
            throw e
        } catch (e: Exception) {
            throw SerializationException(e)
        }

        override fun serialize(encoder: Encoder, value: RpcResponse<Arguments>) =
            throw NotImplementedError()
    }
}

@Serializable
internal data class RpcResponseWithoutArguments(
    @SerialName("result")
    override val result: String,
    @SerialName("arguments")
    override val rawArguments: JsonObject?
) : BaseRpcResponse {
    @Transient
    override lateinit var httpResponse: Response

    @Transient
    override lateinit var requestHeaders: Headers
}

private const val SUCCESS_RESULT = "success"
