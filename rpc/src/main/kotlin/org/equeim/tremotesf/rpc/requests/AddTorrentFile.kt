// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.rpc.requests

import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Base64OutputStream
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException

/**
 * @throws RpcRequestError
 */
suspend fun RpcClient.addTorrentFile(
    torrentFile: ParcelFileDescriptor,
    downloadDirectory: String,
    bandwidthPriority: TorrentLimits.BandwidthPriority,
    unwantedFiles: List<Int>,
    highPriorityFiles: List<Int>,
    lowPriorityFiles: List<Int>,
    renamedFiles: Map<String, String>,
    start: Boolean,
) {
    val response = torrentFile.use {
        performRequest<RpcResponse<AddTorrentFileResponseArguments>, _>(
            org.equeim.tremotesf.rpc.requests.RpcMethod.TorrentAdd,
            AddTorrentFileRequestArguments(
                torrentFile,
                NotNormalizedRpcPath(downloadDirectory),
                unwantedFiles,
                highPriorityFiles,
                lowPriorityFiles,
                bandwidthPriority,
                !start
            ),
            "addTorrentFile"
        )
    }
    if (response.arguments.duplicateTorrent != null) {
        Timber.e("'torrent-duplicate' key is present, torrent is already added")
        throw org.equeim.tremotesf.rpc.RpcRequestError.UnsuccessfulResultField(org.equeim.tremotesf.rpc.requests.DUPLICATE_TORRENT_RESULT, response.httpResponse, response.requestHeaders)
    }
    val torrentHashString = response.arguments.addedTorrent?.hasString ?: return
    renamedFiles.forEach { (path, newName) ->
        renameTorrentFile(torrentHashString, path, newName)
    }
}

@Serializable
private data class AddTorrentFileRequestArguments(
    @Serializable(TorrentFileSerializer::class)
    @SerialName("metainfo")
    val torrentFile: ParcelFileDescriptor,
    @Contextual
    @SerialName("download-dir")
    val downloadDirectory: NotNormalizedRpcPath,
    @SerialName("files-unwanted")
    val unwantedFiles: List<Int>,
    @SerialName("priority-high")
    val highPriorityFiles: List<Int>,
    @SerialName("priority-low")
    val lowPriorityFiles: List<Int>,
    @SerialName("bandwidthPriority")
    val bandwidthPriority: TorrentLimits.BandwidthPriority,
    @SerialName("paused")
    val paused: Boolean,
)

private object TorrentFileSerializer : KSerializer<ParcelFileDescriptor> {
    override val descriptor =
        PrimitiveSerialDescriptor(TorrentFileSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ParcelFileDescriptor = throw NotImplementedError()

    override fun serialize(encoder: Encoder, value: ParcelFileDescriptor) = value.use {
        try {
            val input = FileInputStream(value.fileDescriptor)
            input.use {
                input.channel.position(0)
                val fileSize = input.channel.size()
                if (fileSize <= 0) {
                    throw SerializationException("Torrent file size must be greater than zero")
                }
                Timber.d("Torrent file size is $fileSize bytes")
                val base64Size = 4 * fileSize / 3 + 3 and 3L.inv()
                Timber.d("Base64 size is $base64Size bytes")
                val output = ByteArrayOutputStream(base64Size.toInt())
                Base64OutputStream(output, Base64.NO_WRAP).use {
                    input.copyTo(it)
                }
                val string = output.toString("UTF-8")
                Timber.d("Resulting string length is ${string.length} chars")
                encoder.encodeString(string)
            }
        } catch (e: IOException) {
            throw SerializationException("Failed to read torrent file", e)
        }
    }
}

@Serializable
private data class AddTorrentFileResponseArguments(
    @SerialName("torrent-added")
    val addedTorrent: AddedTorrent? = null,
    @SerialName("torrent-duplicate")
    val duplicateTorrent: Unit? = null,
) {
    @Serializable
    data class AddedTorrent(@SerialName("hashString") val hasString: String)
}

const val DUPLICATE_TORRENT_RESULT = "duplicate torrent"
