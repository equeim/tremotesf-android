// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.serversettings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.MinutesToDurationSerializer
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.RpcRequestBody
import org.equeim.tremotesf.rpc.requests.RpcResponse
import kotlin.time.Duration

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getQueueServerSettings(): QueueServerSettings =
    performRequest<RpcResponse<QueueServerSettings>>(
        QUEUE_SERVER_SETTINGS_REQUEST_BODY,
        "getQueueServerSettings"
    ).arguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setDownloadQueueEnabled(value: Boolean) =
    setSessionProperty("download-queue-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setDownloadQueueSize(value: Int) =
    setSessionProperty("download-queue-size", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setSeedQueueEnabled(value: Boolean) =
    setSessionProperty("seed-queue-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setSeedQueueSize(value: Int) =
    setSessionProperty("seed-queue-size", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setIgnoreQueueIfIdle(value: Boolean) =
    setSessionProperty("queue-stalled-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setIgnoreQueueIfIdleFor(value: Duration) =
    setSessionProperty("queue-stalled-minutes", value, MinutesToDurationSerializer)

@Serializable
data class QueueServerSettings(
    @SerialName("download-queue-enabled")
    val downloadQueueEnabled: Boolean,
    @SerialName("download-queue-size")
    val downloadQueueSize: Int,
    @SerialName("seed-queue-enabled")
    val seedQueueEnabled: Boolean,
    @SerialName("seed-queue-size")
    val seedQueueSize: Int,
    @SerialName("queue-stalled-enabled")
    val ignoreQueueIfIdle: Boolean,
    @Serializable(MinutesToDurationSerializer::class)
    @SerialName("queue-stalled-minutes")
    val ignoreQueueIfIdleFor: Duration,
)

@Serializable
private data class QueueServerSettingsRequestArguments(
    @SerialName("fields")
    val fields: List<String> = QueueServerSettings.serializer().descriptor.elementNames.toList(),
)

private val QUEUE_SERVER_SETTINGS_REQUEST_BODY =
    RpcRequestBody(RpcMethod.SessionGet, QueueServerSettingsRequestArguments())
