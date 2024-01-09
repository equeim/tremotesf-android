// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
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
                val query = this.query
                if (query != null &&
                    (query.contains(MAGNET_QUERY_PREFIX_V1) ||
                            query.contains(MAGNET_QUERY_PREFIX_V2))
                ) {
                    TorrentUri.Type.Link
                } else {
                    null
                }
            } else {
                TorrentUri.Type.Link
            }
        }

        else -> null
    }

fun ClipData.getTorrentUri(context: Context): TorrentUri? =
    items().firstNotNullOfOrNull { it.getTorrentUri(context) }

private fun ClipData.items(): Sequence<ClipData.Item> =
    (0 until itemCount).asSequence().map(::getItemAt)

fun ClipData.Item.getTorrentUri(context: Context): TorrentUri? {
    if (BuildConfig.DEBUG) {
        Timber.d("Processing ClipData.Item with:")
        Timber.d(" - uri: $uri")
        Timber.d(" - intent: $intent")
        Timber.d(" - text: $text")
    }
    return uri()
        .also {
            if (BuildConfig.DEBUG) {
                Timber.d(" - Final URI is $it")
            }
        }
        ?.toTorrentUri(context, validateUri = true)
        .also {
            if (BuildConfig.DEBUG) {
                Timber.d(" - Torrent URI is $it")
            }
        }
}

private fun ClipData.Item.uri(): Uri? {
    uri?.let { return it }
    intent?.data?.let { return it }
    text?.lineSequence()?.forEach { line ->
        runCatching { URI(line).toString().toUri() }.onSuccess { return it }
    }
    return null
}

fun ClipDescription.mimeTypes(): List<String> = (0 until mimeTypeCount).map(::getMimeType)

private const val TORRENT_FILE_SUFFIX = ".torrent"
private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
private const val SCHEME_MAGNET = "magnet"
private const val MAGNET_QUERY_PREFIX_V1 = "xt=urn:btih:"
private const val MAGNET_QUERY_PREFIX_V2 = "xt=urn:btmh:"
