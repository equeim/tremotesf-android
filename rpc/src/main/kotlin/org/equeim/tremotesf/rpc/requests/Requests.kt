// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
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

@Serializable
internal data class RequestWithFields(
    @SerialName("fields")
    val fields: List<String>,
) {
    constructor(vararg fields: String) : this(fields.toList())
}

@Serializable
internal data class RawRpcResponse(
    @SerialName("result")
    val result: String,
    @SerialName("arguments")
    val arguments: JsonObject?,
) {
    val isSuccessful: Boolean get() = result == SUCCESS_RESULT
}

internal data class RpcResponse<ResponseArguments : Any>(
    val arguments: ResponseArguments,
    val httpResponse: Response,
    val requestHeaders: Headers,
)

private const val SUCCESS_RESULT = "success"
