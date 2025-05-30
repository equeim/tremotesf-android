// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.serversettings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.MinutesSinceStartOfDaySerializer
import org.equeim.tremotesf.rpc.requests.RequestWithFields
import org.equeim.tremotesf.rpc.requests.RpcEnum
import org.equeim.tremotesf.rpc.requests.RpcMethod
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.createStaticRpcRequestBody
import java.time.LocalTime

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getSpeedServerSettings(): SpeedServerSettings =
    performRequest<SpeedServerSettings>(
        SPEED_SERVER_SETTINGS_REQUEST_BODY,
        "getSpeedServerSettings"
    ).arguments

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setDownloadSpeedLimited(value: Boolean) =
    setSessionProperty("speed-limit-down-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setDownloadSpeedLimit(value: TransferRate) =
    setSessionProperty("speed-limit-down", value, TransferRate.KiloBytesPerSecondSerializer)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUploadSpeedLimited(value: Boolean) =
    setSessionProperty("speed-limit-up-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setUploadSpeedLimit(value: TransferRate) =
    setSessionProperty("speed-limit-up", value, TransferRate.KiloBytesPerSecondSerializer)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeLimitsEnabled(value: Boolean) =
    setSessionProperty("alt-speed-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeDownloadSpeedLimit(value: TransferRate) =
    setSessionProperty("alt-speed-down", value, TransferRate.KiloBytesPerSecondSerializer)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeUploadSpeedLimit(value: TransferRate) =
    setSessionProperty("alt-speed-up", value, TransferRate.KiloBytesPerSecondSerializer)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeLimitsScheduled(value: Boolean) =
    setSessionProperty("alt-speed-time-enabled", value)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeLimitsBeginTime(value: LocalTime) =
    setSessionProperty(
        "alt-speed-time-begin", value,
        MinutesSinceStartOfDaySerializer
    )

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeLimitsEndTime(value: LocalTime) =
    setSessionProperty("alt-speed-time-end", value, MinutesSinceStartOfDaySerializer)

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.setAlternativeLimitsDays(value: SpeedServerSettings.AlternativeLimitsDays) =
    setSessionProperty("alt-speed-time-day", value)

@Serializable
data class SpeedServerSettings(
    @SerialName("speed-limit-down-enabled")
    val downloadSpeedLimited: Boolean,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("speed-limit-down")
    val downloadSpeedLimit: TransferRate,
    @SerialName("speed-limit-up-enabled")
    val uploadSpeedLimited: Boolean,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("speed-limit-up")
    val uploadSpeedLimit: TransferRate,
    @SerialName("alt-speed-enabled")
    val alternativeLimitsEnabled: Boolean,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("alt-speed-down")
    val alternativeDownloadSpeedLimit: TransferRate,
    @Serializable(TransferRate.KiloBytesPerSecondSerializer::class)
    @SerialName("alt-speed-up")
    val alternativeUploadSpeedLimit: TransferRate,
    @SerialName("alt-speed-time-enabled")
    val alternativeLimitsScheduled: Boolean,
    @Serializable(MinutesSinceStartOfDaySerializer::class)
    @SerialName("alt-speed-time-begin")
    val alternativeLimitsBeginTime: LocalTime,
    @Serializable(MinutesSinceStartOfDaySerializer::class)
    @SerialName("alt-speed-time-end")
    val alternativeLimitsEndTime: LocalTime,
    @SerialName("alt-speed-time-day")
    val alternativeLimitsDays: AlternativeLimitsDays,
) {
    @Serializable(AlternativeLimitsDays.Serializer::class)
    enum class AlternativeLimitsDays(override val rpcValue: Int) : RpcEnum {
        Sunday(1),
        Monday(2),
        Tuesday(4),
        Wednesday(8),
        Thursday(16),
        Friday(32),
        Saturday(64),
        Weekdays(62),
        Weekends(65),
        All(127);

        internal object Serializer : RpcEnum.Serializer<AlternativeLimitsDays>(AlternativeLimitsDays::class)
    }
}

private val SPEED_SERVER_SETTINGS_REQUEST_BODY = createStaticRpcRequestBody(
    RpcMethod.SessionGet,
    RequestWithFields(SpeedServerSettings.serializer().descriptor.elementNames.toList())
)
