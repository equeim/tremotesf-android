// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError

/**
 * @return Free space in default download directory
 * @throws RpcRequestError
 */
suspend fun RpcClient.getDownloadDirFreeSpace(): FileSize {
    return performRequest<RpcResponse<FreeSpaceResponseArguments>, _>(
        RpcMethod.FreeSpace,
        FreeSpaceRequestArgumentsWithNormalizedPath(getDownloadDirectory().value),
        "getDownloadDirFreeSpace"
    ).arguments.freeSpace
}

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getFreeSpaceInDirectory(directory: String): FileSize =
    performRequest<RpcResponse<FreeSpaceResponseArguments>, _>(
        RpcMethod.FreeSpace,
        FreeSpaceRequestArguments(NotNormalizedRpcPath(directory))
    ).arguments.freeSpace

@Serializable
private data class FreeSpaceRequestArguments(
    @Contextual
    @SerialName("path")
    val path: NotNormalizedRpcPath,
)

@Serializable
internal data class FreeSpaceRequestArgumentsWithNormalizedPath(
    @SerialName("path")
    val path: String,
)

@Serializable
internal data class FreeSpaceResponseArguments(
    @SerialName("size-bytes")
    val freeSpace: FileSize,
)
