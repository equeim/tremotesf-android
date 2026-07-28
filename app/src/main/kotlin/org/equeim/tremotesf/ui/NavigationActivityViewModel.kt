// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.lifecycle.AndroidViewModel
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileDestination
import org.equeim.tremotesf.ui.addtorrent.AddTorrentLinkDestination
import org.equeim.tremotesf.ui.addtorrent.TORRENT_FILE_MIME_TYPE
import org.equeim.tremotesf.ui.addtorrent.TORRENT_LINK_MIME_TYPES
import org.equeim.tremotesf.ui.addtorrent.TorrentUri
import org.equeim.tremotesf.ui.addtorrent.getTorrentUris
import org.equeim.tremotesf.ui.addtorrent.toTorrentUri
import org.equeim.tremotesf.ui.connectionsettings.ServerEditDestination
import org.equeim.tremotesf.ui.torrentproperties.TorrentPropertiesDestination
import org.equeim.tremotesf.ui.torrentslist.TorrentsListDestination
import timber.log.Timber

class NavigationActivityViewModel(application: Application) : AndroidViewModel(application) {
    fun getInitialDestinations(intent: Intent, isTaskRoot: Boolean): List<Destination> {
        Timber.d("getInitialDestinations() called with: intent = $intent, isTaskRoot = $isTaskRoot")
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            val deepLinkDestination = getDeepLinkDestination(intent)
            if (deepLinkDestination != null) {
                return if (isTaskRoot) {
                    listOf(TorrentsListDestination, deepLinkDestination)
                } else {
                    listOf(deepLinkDestination)
                }
            }
        }
        return if (GlobalServers.serversState.value.servers.isEmpty()) {
            listOf(TorrentsListDestination, ServerEditDestination())
        } else {
            listOf(TorrentsListDestination)
        }
    }

    fun getDeepLinkDestination(intent: Intent): Destination? {
        Timber.d("getDeepLinkDestination() called with: intent = $intent")
        if (intent.action != Intent.ACTION_VIEW) {
            Timber.d("getDeepLinkDestination: action is not VIEW")
            return null
        }
        val uri = intent.data
        if (uri == null) {
            Timber.d("getDeepLinkDestination: data is null")
            return null
        }
        Timber.d("getDeepLinkDestination: data = $uri")
        return uri.parseAsInternalDeepLink()
            ?: uri.toTorrentUri(getApplication(), validateUri = false)?.let {
                getAddTorrentDestination(listOf(it))
            }
    }

    fun shouldStartDragAndDrop(startEvent: DragAndDropEvent): Boolean {
        val mimeTypes = startEvent.mimeTypes()
        Timber.i("Received shouldStartDragAndDrop event, mime types = $mimeTypes")
        val ok = mimeTypes.contains(TORRENT_FILE_MIME_TYPE) || TORRENT_LINK_MIME_TYPES.any(mimeTypes::contains)
        if (ok) {
            Timber.i("Accepting shouldStartDragAndDrop event")
        } else {
            Timber.i("Rejecting shouldStartDragAndDrop event")
        }
        return ok
    }

    fun getAddTorrentDestination(event: DragAndDropEvent): Destination? {
        return getAddTorrentDestination(event.toAndroidDragEvent().clipData.getTorrentUris(getApplication()))
    }

    private fun getAddTorrentDestination(uris: List<TorrentUri>): Destination? {
        if (uris.isEmpty()) return null
        val firstUri = uris.first()
        return when (firstUri.type) {
            TorrentUri.Type.File -> AddTorrentFileDestination(uri = firstUri.uri)
            TorrentUri.Type.Link -> AddTorrentLinkDestination(
                uris = uris.mapNotNull { it.takeIf { it.type == TorrentUri.Type.Link }?.uri }
            )
        }
    }
}

fun Destination.toInternalDeepLink(): Uri {
    val builder = Uri.Builder().scheme(INTERNAL_DEEP_LINK_SCHEME).authority(INTERNAL_DEEP_LINK_AUTHORITY)
    when (this) {
        is TorrentPropertiesDestination ->
            builder.appendPath(TORRENT_PROPERTIES_PATH)
                .appendPath(torrentHashString)

        else -> throw IllegalArgumentException("Destination $this can't be used in internal deep link")
    }
    return builder.build()
}

fun Uri.parseAsInternalDeepLink(): Destination? {
    if (scheme != INTERNAL_DEEP_LINK_SCHEME) return null
    if (authority != INTERNAL_DEEP_LINK_AUTHORITY) return null
    val pathSegments = this.pathSegments
    if (pathSegments.getOrNull(0) != TORRENT_PROPERTIES_PATH) return null
    val torrentHashString = pathSegments.getOrNull(1) ?: return null
    return TorrentPropertiesDestination(torrentHashString).also {
        Timber.d("Parsed $this as $it")
    }
}

private const val INTERNAL_DEEP_LINK_SCHEME = "tremotesf"
private const val INTERNAL_DEEP_LINK_AUTHORITY = "org.equeim.tremotesf"
private const val TORRENT_PROPERTIES_PATH = "torrentProperties"
