// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.torrentfile.SCHEME_MAGNET
import org.equeim.tremotesf.torrentfile.parseMagnetLink
import timber.log.Timber
import java.net.URI

const val TORRENT_FILE_MIME_TYPE = "application/x-bittorrent"
val TORRENT_LINK_MIME_TYPES = listOf(
    ClipDescription.MIMETYPE_TEXT_URILIST,
    ClipDescription.MIMETYPE_TEXT_INTENT,
    ClipDescription.MIMETYPE_TEXT_PLAIN
)

data class TorrentUri(val uri: Uri, val type: Type) {
    enum class Type {
        File,
        Link
    }
}

fun Uri.toTorrentUri(context: Context, validateUri: Boolean): TorrentUri? =
    getTorrentUriType(context, validateUri)?.let { TorrentUri(this, it) }

private fun Uri.getTorrentUriType(context: Context, validateUri: Boolean): TorrentUri.Type? =
    when (scheme) {
        ContentResolver.SCHEME_CONTENT -> if (validateUri) {
            if (context.contentResolver.getType(this) == TORRENT_FILE_MIME_TYPE) {
                TorrentUri.Type.File
            } else {
                null
            }
        } else {
            TorrentUri.Type.File
        }

        ContentResolver.SCHEME_FILE -> if (validateUri) {
            if (path?.endsWith(TORRENT_FILE_SUFFIX) == true) {
                TorrentUri.Type.File
            } else {
                null
            }
        } else {
            TorrentUri.Type.File
        }

        SCHEME_HTTP, SCHEME_HTTPS -> TorrentUri.Type.Link
        SCHEME_MAGNET -> {
            if (validateUri) {
                try {
                    parseMagnetLink(this)
                    TorrentUri.Type.Link
                } catch (_: IllegalArgumentException) {
                    null
                }
            } else {
                TorrentUri.Type.Link
            }
        }

        else -> null
    }

fun ClipData.getTorrentUris(context: Context): List<TorrentUri> =
    items().flatMap { it.getTorrentUris(context) }.toList()

private fun ClipData.items(): Sequence<ClipData.Item> =
    (0 until itemCount).asSequence().map(::getItemAt)

fun ClipData.Item.getTorrentUris(context: Context): List<TorrentUri> {
    if (BuildConfig.DEBUG) {
        Timber.d("Processing ClipData.Item with:")
        Timber.d(" - uri: $uri")
        Timber.d(" - intent: $intent")
        Timber.d(" - text: $text")
    }
    return uris()
        .also {
            if (BuildConfig.DEBUG) {
                Timber.d(" - Final URIs are:\n ${it.joinToString("\n ")}")
            }
        }
        .mapNotNull { it.toTorrentUri(context, validateUri = true) }
        .also {
            if (BuildConfig.DEBUG) {
                Timber.d(" - Torrent URIs are:\n ${it.joinToString("\n ")}")
            }
        }
}

private fun ClipData.Item.uris(): List<Uri> {
    uri?.let { return listOf(it) }
    intent?.data?.let { return listOf(it) }
    return text?.lineSequence()?.mapNotNull { line ->
        runCatching { URI(line).toString().toUri() }.getOrNull()
    }?.toList().orEmpty()
}

fun ClipDescription.mimeTypes(): List<String> = (0 until mimeTypeCount).map(::getMimeType)

private const val TORRENT_FILE_SUFFIX = ".torrent"
private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
