// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Headers
import okhttp3.Response
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
 * @throws TorrentAlreadyExists
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
    labels: List<String>,
) {
    val response = handleDuplicateTorrentError(AddTorrentFileResponseArguments::duplicateTorrent) {
        torrentFile.use {
            performRequest<RpcResponse<AddTorrentFileResponseArguments>, _>(
                method = RpcMethod.TorrentAdd,
                arguments = AddTorrentFileRequestArguments(
                    torrentFile = torrentFile,
                    downloadDirectory = NotNormalizedRpcPath(downloadDirectory),
                    unwantedFiles = unwantedFiles,
                    highPriorityFiles = highPriorityFiles,
                    lowPriorityFiles = lowPriorityFiles,
                    bandwidthPriority = bandwidthPriority,
                    paused = !start,
                    labels = labels
                ),
                callerContext = "addTorrentFile"
            )
        }
    }
    val torrentHashString = response.arguments.addedTorrent?.hashString ?: return
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
    @SerialName("labels")
    val labels: List<String>,
)

private object TorrentFileSerializer : KSerializer<ParcelFileDescriptor> {
    override val descriptor =
        PrimitiveSerialDescriptor(
            TorrentFileSerializer::class.qualifiedName!!,
            PrimitiveKind.STRING
        )

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
    val duplicateTorrent: DuplicateTorrent? = null,
) {
    @Serializable
    data class AddedTorrent(@SerialName("hashString") val hashString: String)
}

private const val DUPLICATE_TORRENT_RESULT = "duplicate torrent"

@Serializable
internal data class DuplicateTorrentResponseArguments(
    @SerialName("torrent-duplicate")
    val duplicateTorrent: DuplicateTorrent
)

@Serializable
internal data class DuplicateTorrent(
    @SerialName("hashString")
    val hashString: String,
    @SerialName("name")
    val name: String,
)

internal inline fun <ResponseArguments : Any> RpcClient.handleDuplicateTorrentError(
    duplicateTorrentFromResponse: ResponseArguments.() -> DuplicateTorrent?,
    performRequest: () -> RpcResponse<ResponseArguments>
): RpcResponse<ResponseArguments> {
    val response = try {
        performRequest()
    } catch (e: RpcRequestError.UnsuccessfulResultField) {
        throw if (e.result == DUPLICATE_TORRENT_RESULT && e.rawArguments != null) {
            try {
                TorrentAlreadyExists(
                    duplicateTorrent = json.decodeFromJsonElement<DuplicateTorrentResponseArguments>(
                        e.rawArguments
                    ).duplicateTorrent,
                    response = e.response,
                    requestHeaders = e.requestHeaders
                )
            } catch (duplicateTorrentParsingError: SerializationException) {
                Timber.e(duplicateTorrentParsingError, "Failed to parse duplicate torrent response")
                e
            }
        } else {
            e
        }
    }
    response.arguments.duplicateTorrentFromResponse()?.let {
        Timber.e("'torrent-duplicate' key is present, torrent is already added")
        throw TorrentAlreadyExists(
            duplicateTorrent = it,
            response = response.httpResponse,
            requestHeaders = response.requestHeaders
        )
    }
    return response
}

class TorrentAlreadyExists internal constructor(
    duplicateTorrent: DuplicateTorrent,
    response: Response,
    requestHeaders: Headers,
) :
    RpcRequestError.RequestSpecificError(
        response = response,
        requestHeaders = requestHeaders,
        message = "Torrent with info hash ${duplicateTorrent.hashString} and name \"${duplicateTorrent.name}\" already exists"
    ) {
        val torrentName: String = duplicateTorrent.name
    }
