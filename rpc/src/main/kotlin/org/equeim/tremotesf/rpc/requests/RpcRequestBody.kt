// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.encodeToBufferedSink
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import java.io.IOException

internal class RpcRequestBody internal constructor(
    internal val method: RpcMethod,
    encodeToBuffer: () -> Buffer,
) : RequestBody() {
    private val body: Buffer by lazy {
        try {
            encodeToBuffer()
        } catch (e: Exception) {
            throw IOException("Failed to serialize request body", e)
        }
    }

    override fun contentType(): MediaType = JSON_MEDIA_TYPE
    override fun contentLength(): Long = body.size
    override fun writeTo(sink: BufferedSink) {
        sink.writeAll(body.peek())
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun <T> RpcRequestBody(body: T, serializer: KSerializer<T>, method: RpcMethod, json: Json) =
    RpcRequestBody(method) {
        Buffer().apply { json.encodeToBufferedSink(serializer, body, this) }
    }

internal inline fun <reified RequestArgumentsT> RpcRequestBody(
    method: RpcMethod,
    arguments: RequestArgumentsT,
    json: Json,
) = RpcRequestBody(
    body = RpcRequest(method = method, arguments = arguments),
    serializer = RpcRequest.serializer(serializer<RequestArgumentsT>()),
    method = method,
    json = json
)

internal inline fun <reified RequestArgumentsT> createStaticRpcRequestBody(
    method: RpcMethod,
    arguments: RequestArgumentsT,
) = RpcRequestBody(method = method, arguments = arguments, json = STATIC_REQUEST_BODY_JSON)

internal fun createStaticRpcRequestBody(method: RpcMethod) =
    RpcRequestBody(
        body = RpcRequestWithoutArguments(method),
        serializer = RpcRequestWithoutArguments.serializer(),
        method = method,
        json = STATIC_REQUEST_BODY_JSON
    )

private val STATIC_REQUEST_BODY_JSON = Json
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
