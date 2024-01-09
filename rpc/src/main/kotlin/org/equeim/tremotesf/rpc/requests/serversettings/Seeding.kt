// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.serversettings

import kotlinx.serialization.ExperimentalSerializationApi
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
suspend fun RpcClient.getSeedingServerSettings(): SeedingServerSettings =
    performRequest<RpcResponse<SeedingServerSettings>>(
        SEEDING_SERVER_SETTINGS_REQUEST_BODY,
        "getSeedingServerSettings"
    ).arguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setServerRatioLimited(value: Boolean) =
    setSessionProperty("seedRatioLimited", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setServerRatioLimit(value: Double) =
    setSessionProperty("seedRatioLimit", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setServerIdleSeedingLimited(value: Boolean) =
    setSessionProperty("idle-seeding-limit-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setServerIdleSeedingLimit(value: Duration) =
    setSessionProperty("idle-seeding-limit", value, org.equeim.tremotesf.rpc.requests.MinutesToDurationSerializer)

@Serializable
data class SeedingServerSettings(
    @SerialName("seedRatioLimited")
    val ratioLimited: Boolean,
    @SerialName("seedRatioLimit")
    val ratioLimit: Double,
    @SerialName("idle-seeding-limit-enabled")
    val idleSeedingLimited: Boolean,
    @Serializable(MinutesToDurationSerializer::class)
    @SerialName("idle-seeding-limit")
    val idleSeedingLimit: Duration,
)

@Serializable
private data class SeedingServerSettingsRequestArguments(
    @SerialName("fields")
    @OptIn(ExperimentalSerializationApi::class)
    val fields: List<String> = SeedingServerSettings.serializer().descriptor.elementNames.toList(),
)

private val SEEDING_SERVER_SETTINGS_REQUEST_BODY =
    RpcRequestBody(RpcMethod.SessionGet, SeedingServerSettingsRequestArguments())
