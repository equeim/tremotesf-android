// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementNames

internal val SERVER_VERSION_REQUEST = createStaticRpcRequestBody(
    RpcMethod.SessionGet,
    RequestWithFields(ServerVersionResponseArguments.serializer().descriptor.elementNames.toList())
)

@Serializable
internal data class ServerVersionResponseArguments(
    @SerialName("rpc-version")
    val rpcVersion: Int,
    @SerialName("rpc-version-minimum")
    val minimumRpcVersion: Int,
    @SerialName("version")
    val version: String,
)
