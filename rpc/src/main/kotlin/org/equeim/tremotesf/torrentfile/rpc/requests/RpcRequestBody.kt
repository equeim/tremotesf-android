// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

internal class RpcRequestBody internal constructor(
    internal val method: RpcMethod,
    encodeToString: () -> String,
) : RequestBody() {
    private val body: ByteArray by lazy { encodeToString().encodeToByteArray() }

    override fun contentType(): MediaType = JSON_MEDIA_TYPE
    override fun contentLength(): Long = body.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        sink.write(body)
    }
}

private fun <T> RpcRequestBody(body: T, serializer: KSerializer<T>, method: RpcMethod, json: Json) =
    RpcRequestBody(method) { json.encodeToString(serializer, body) }

internal inline fun <reified RequestArgumentsT> RpcRequestBody(
    method: RpcMethod,
    arguments: RequestArgumentsT,
    json: Json = STATIC_JSON,
) = RpcRequestBody(RpcRequest(method, arguments), serializer(), method, json)

internal fun RpcRequestBody(
    requestBody: RpcRequestWithoutArguments,
    json: Json = STATIC_JSON,
) = RpcRequestBody(requestBody, RpcRequestWithoutArguments.serializer(), requestBody.method, json)

private val STATIC_JSON = Json { encodeDefaults = true }
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
