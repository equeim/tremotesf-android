// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

@ConsistentCopyVisibility
@Suppress("DataClassPrivateConstructor")
@Serializable
internal data class TorrentGetRequestForFields private constructor(
    @SerialName("ids")
    val torrentsHashStrings: List<String>,

    @SerialName("fields")
    val fields: List<String>,
) {
    constructor(torrentHashString: String, field: String) : this(listOf(torrentHashString), listOf(field))
    constructor(torrentHashString: String, fields: List<String>) : this(listOf(torrentHashString), fields)
}

@Serializable
internal data class TorrentGetResponseForFields<FieldsObject : Any>(
    @SerialName("torrents")
    val torrents: List<FieldsObject>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }
}

/**
 * @return Torrent's name, if it exists
 * @throws RpcRequestError
 */
suspend fun RpcClient.checkIfTorrentExists(hashString: String): String? =
    performRequest<RpcResponse<TorrentGetResponseForFields<TorrentExistsFields>>, _>(
        RpcMethod.TorrentGet,
        TorrentGetRequestForFields(hashString, "name"),
        "checkIfTorrentExists"
    ).arguments.torrents.firstOrNull()?.name

@Serializable
private data class TorrentExistsFields(
    @SerialName("name")
    val name: String
)
