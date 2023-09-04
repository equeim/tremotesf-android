// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import org.equeim.tremotesf.torrentfile.rpc.requests.FreeSpaceRequestArgumentsWithNormalizedPath
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcMethod
import org.equeim.tremotesf.torrentfile.rpc.requests.RpcRequestBody
import org.equeim.tremotesf.torrentfile.rpc.requests.ServerVersionResponseArguments

class ServerCapabilities internal constructor(
    private val rpcVersion: Int,
    val serverOs: ServerOs,
) {
    val hasTableMode: Boolean get() = rpcVersion >= 16

    enum class ServerOs {
        UnixLike,
        Windows
    }

    override fun toString(): String = "ServerCapabilities(hasTableMode=$hasTableMode, serverOs=$serverOs)"
}

internal val ServerVersionResponseArguments.isSupported: Boolean
    get() = MINIMUM_RPC_VERSION in minimumRpcVersion..rpcVersion

internal const val MINIMUM_RPC_VERSION = 15

internal val UNIX_ROOT_FREE_SPACE_REQUEST = RpcRequestBody(
    RpcMethod.FreeSpace,
    FreeSpaceRequestArgumentsWithNormalizedPath("/")
)
