// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError

/**
 * @return Free space in default download directory
 * @throws RpcRequestError
 */
suspend fun RpcClient.getDownloadDirFreeSpace(): FileSize {
    return performRequest<FreeSpaceResponseArguments, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.FreeSpace,
        FreeSpaceRequestArgumentsWithNormalizedPath(getDownloadDirectory().value),
        "getDownloadDirFreeSpace"
    ).arguments.freeSpace
}

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.getFreeSpaceInDirectory(directory: String): FileSize =
    performRequest<FreeSpaceResponseArguments, _>(
        org.equeim.tremotesf.rpc.requests.RpcMethod.FreeSpace,
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
