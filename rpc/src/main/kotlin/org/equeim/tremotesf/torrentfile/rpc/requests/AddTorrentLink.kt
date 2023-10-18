// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc.requests

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.tremotesf.torrentfile.rpc.RpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.TorrentLimits
import timber.log.Timber

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.addTorrentLink(
    url: String,
    downloadDirectory: String,
    bandwidthPriority: TorrentLimits.BandwidthPriority,
    start: Boolean,
) {
    val response = performRequest<RpcResponse<AddTorrentLinkResponseArguments>, _>(
        RpcMethod.TorrentAdd,
        AddTorrentLinkRequestArguments(url, NotNormalizedRpcPath(downloadDirectory), bandwidthPriority, !start),
        "addTorrentLink"
    )
    if (response.arguments.duplicateTorrent != null) {
        Timber.e("'torrent-duplicate' key is present, torrent is already added")
        throw RpcRequestError.UnsuccessfulResultField(DUPLICATE_TORRENT_RESULT, response.httpResponse, response.requestHeaders)
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
)

@Serializable
private data class AddTorrentLinkResponseArguments(
    @SerialName("torrent-duplicate")
    val duplicateTorrent: Unit? = null,
)
