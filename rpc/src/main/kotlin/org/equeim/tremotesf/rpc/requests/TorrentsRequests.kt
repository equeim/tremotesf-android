// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestContext

internal suspend inline fun <reified FieldsObject : Any> RpcClient.performAllTorrentsRequest(
    objectsFormatRequestBody: RpcRequestBody,
    tableFormatRequestBody: RpcRequestBody,
    callerContext: String
): List<FieldsObject> =
    if (checkServerCapabilities(
            force = false,
            context = RpcRequestContext(RpcMethod.TorrentGet, callerContext)
        ).hasTableMode
    ) {
        performRequest<RpcResponse<AllTorrentsTableFormatResponseArguments<FieldsObject>>>(
            tableFormatRequestBody,
            callerContext
        ).arguments.torrents
    } else {
        performRequest<RpcResponse<AllTorrentsObjectsFormatResponseArguments<FieldsObject>>>(
            objectsFormatRequestBody,
            callerContext
        ).arguments.torrents
    }

@ConsistentCopyVisibility
@Serializable
internal data class AllTorrentsRequestArguments private constructor(
    @SerialName("fields")
    val fields: List<String>,
    @SerialName("format")
    val format: String? = null,
) {
    constructor(fields: List<String>, table: Boolean) : this(fields = fields, format = if (table) "table" else null)
}

@Serializable
internal data class AllTorrentsObjectsFormatResponseArguments<FieldsObject : Any>(
    @SerialName("torrents")
    val torrents: List<FieldsObject>,
)

@Serializable
internal data class AllTorrentsTableFormatResponseArguments<FieldsObject : Any>(
    @Serializable(TorrentsListTableFormatSerializer::class)
    @SerialName("torrents")
    val torrents: List<FieldsObject>,
)

private class TorrentsListTableFormatSerializer<FieldsObject : Any>(
    torrentSerializer: KSerializer<FieldsObject>
) : JsonTransformingSerializer<List<FieldsObject>>(ListSerializer(torrentSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonArray) {
            throw SerializationException("Torrents list must be an array")
        }
        if (element.isEmpty()) {
            // Shouldn't be possible but return empty list just in case
            return element
        }
        val firstElement = element.first()
        if (firstElement !is JsonArray) {
            throw SerializationException("Elements of torrents array in table format must be arrays")
        }
        // Table format
        if (element.size == 1) {
            // Empty list in table format
            return JsonArray(emptyList())
        }
        val fieldNames = firstElement.map { fieldName ->
            if (fieldName !is JsonPrimitive || !fieldName.isString) {
                throw SerializationException("Field name must be a string")
            }
            fieldName.content
        }
        return JsonArray(element.subList(1, element.size).map { fieldValues ->
            if (fieldValues !is JsonArray) {
                throw SerializationException("Elements of torrents array in table format must be arrays")
            }
            JsonObject(
                fieldValues.withIndex().associateBy(
                    keySelector = { fieldNames[it.index] },
                    valueTransform = { it.value }
                ))
        })
    }
}

@ConsistentCopyVisibility
@Serializable
internal data class SingleTorrentRequestArguments private constructor(
    @SerialName("ids")
    val torrentsHashStrings: List<String>,
    @SerialName("fields")
    val fields: List<String>,
) {
    constructor(torrentHashString: String, fields: List<String>) : this(listOf(torrentHashString), fields)
    constructor(torrentHashString: String, field: String) : this(torrentHashString, listOf(field))
}

@Serializable
internal data class SingleTorrentResponseArguments<FieldsObject : Any>(
    @SerialName("torrents")
    val torrents: List<FieldsObject>,
) {
    init {
        if (torrents.size > 1) {
            throw SerializationException("'torrents' array must not contain more than one element")
        }
    }
}
