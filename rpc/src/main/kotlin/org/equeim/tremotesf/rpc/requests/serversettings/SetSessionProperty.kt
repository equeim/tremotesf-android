// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests.serversettings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @throws RpcRequestError
 */
internal suspend inline fun <reified T> RpcClient.setSessionProperty(
    property: String,
    value: T,
): Unit =
    setSessionProperty(property, value, serializer())

/**
 * @throws RpcRequestError
 */
internal suspend fun <T> RpcClient.setSessionProperty(
    property: String,
    value: T,
    serializer: KSerializer<T>,
) {
    performRequest<Unit, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.SessionSet,
        buildJsonObject { put(property, json.encodeToJsonElement(serializer, value)) },
        property
    )
}
