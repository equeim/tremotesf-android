// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.addTorrentLink(
    url: String,
    downloadDirectory: String,
    bandwidthPriority: TorrentLimits.BandwidthPriority,
    start: Boolean,
    labels: List<String>,
) {
    handleDuplicateTorrentError(AddTorrentLinkResponseArguments::duplicateTorrent) {
        performRequest<AddTorrentLinkResponseArguments, _>(
            method = RpcMethod.TorrentAdd,
            arguments = AddTorrentLinkRequestArguments(
                url = url,
                downloadDirectory = NotNormalizedRpcPath(downloadDirectory),
                bandwidthPriority = bandwidthPriority,
                paused = !start,
                labels = labels
            ),
            callerContext = "addTorrentLink"
        )
    }
}

@Serializable
private data class AddTorrentLinkRequestArguments(
    @SerialName("filename")
    val url: String,
    @Contextual
    @SerialName("download-dir")
    val downloadDirectory: NotNormalizedRpcPath,
    @SerialName("bandwidthPriority")
    val bandwidthPriority: TorrentLimits.BandwidthPriority,
    @SerialName("paused")
    val paused: Boolean,
    @SerialName("labels")
    val labels: List<String>,
)

@Serializable
private data class AddTorrentLinkResponseArguments(
    @SerialName("torrent-duplicate")
    val duplicateTorrent: DuplicateTorrent? = null,
)
