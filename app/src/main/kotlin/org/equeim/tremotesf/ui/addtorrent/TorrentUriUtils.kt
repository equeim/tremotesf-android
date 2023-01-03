package org.equeim.tremotesf.ui.addtorrent

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
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

fun Uri.toTorrentUri(context: Context, checkContentUriType: Boolean): TorrentUri? =
    getTorrentUriType(context, checkContentUriType)?.let { TorrentUri(this, it) }

private fun Uri.getTorrentUriType(context: Context, checkContentUriType: Boolean): TorrentUri.Type? =
    when (scheme) {
        ContentResolver.SCHEME_CONTENT -> if (checkContentUriType) {
            if (context.contentResolver.getType(this) == TORRENT_FILE_MIME_TYPE) {
                TorrentUri.Type.File
            } else {
                null
            }
        } else {
            TorrentUri.Type.File
        }
        ContentResolver.SCHEME_FILE -> if (path?.endsWith(TORRENT_FILE_SUFFIX) == true) {
            TorrentUri.Type.File
        } else {
            null
        }
        SCHEME_HTTP, SCHEME_HTTPS -> TorrentUri.Type.Link
        SCHEME_MAGNET -> {
            val query = this.query
            if (query != null &&
                (query.startsWith(MAGNET_QUERY_PREFIX_V1) ||
                        query.startsWith(MAGNET_QUERY_PREFIX_V2))
            ) {
                TorrentUri.Type.Link
            } else {
                null
            }
        }
        else -> null
    }

fun ClipData.getTorrentUri(context: Context): TorrentUri? =
    items().firstNotNullOfOrNull { it.getTorrentUri(context) }

private fun ClipData.items(): Sequence<ClipData.Item> =
    (0 until itemCount).asSequence().map(::getItemAt)

fun ClipData.Item.getTorrentUri(context: Context): TorrentUri? =
    uri()?.toTorrentUri(context, checkContentUriType = true)

private fun ClipData.Item.uri(): Uri? {
    uri?.let { return it }
    intent?.data?.let { return it }
    text?.lineSequence()?.forEach { line ->
        runCatching { URI(line).toString().toUri() }.onSuccess { return it }
    }
    return null
}

private const val TORRENT_FILE_SUFFIX = ".torrent"
private const val SCHEME_HTTP = "http"
private const val SCHEME_HTTPS = "https"
private const val SCHEME_MAGNET = "magnet"
private const val MAGNET_QUERY_PREFIX_V1 = "xt=urn:btih:"
private const val MAGNET_QUERY_PREFIX_V2 = "xt=urn:btmh:"
