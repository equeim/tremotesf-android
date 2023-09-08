// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDeepLinkBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.addtorrent.AddTorrentFileFragmentArgs
import org.equeim.tremotesf.ui.addtorrent.AddTorrentLinkFragmentArgs
import org.equeim.tremotesf.ui.addtorrent.TORRENT_FILE_MIME_TYPE
import org.equeim.tremotesf.ui.addtorrent.TORRENT_LINK_MIME_TYPES
import org.equeim.tremotesf.ui.addtorrent.TorrentUri
import org.equeim.tremotesf.ui.addtorrent.getTorrentUri
import org.equeim.tremotesf.ui.addtorrent.mimeTypes
import org.equeim.tremotesf.ui.addtorrent.toTorrentUri
import org.equeim.tremotesf.ui.utils.savedState
import timber.log.Timber

class NavigationActivityViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    var navigatedInitially by savedState(savedStateHandle, false)

    data class AddTorrentDirections(@IdRes val destinationId: Int, val arguments: Bundle)

    fun getAddTorrentDirections(intent: Intent): AddTorrentDirections? {
        if (intent.action != Intent.ACTION_VIEW) return null
        return intent.data
            ?.toTorrentUri(getApplication(), validateUri = false)
            ?.let(::getAddTorrentDirections)
    }

    fun getInitialDeepLinkIntent(intent: Intent): Intent? {
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            Timber.w("getInitialDeepLinkIntent: activity was launched from history, return null")
            return null
        }

        var deepLinkIntent: Intent? = getAddTorrentDirections(intent)?.run {
            createDeepLinkIntent(
                destinationId,
                arguments,
                intent
            )
        }
        if (deepLinkIntent == null) {
            if ((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0) {
                if (GlobalServers.serversState.value.servers.isEmpty()) {
                    deepLinkIntent = createDeepLinkIntent(R.id.server_edit_fragment, null, intent)
                }
            } else {
                Timber.i("getInitialDeepLinkIntent: we are not on our own task, return null")
            }
        }
        return deepLinkIntent
    }

    private fun createDeepLinkIntent(
        @IdRes destinationId: Int,
        arguments: Bundle?,
        originalIntent: Intent,
    ): Intent {
        return NavDeepLinkBuilder(getApplication())
            .setGraph(R.navigation.nav_main)
            .setDestination(destinationId)
            .setArguments(arguments)
            .createTaskStackBuilder()
            .intents
            .single()
            .apply {
                // Restore original intent's flags
                flags = originalIntent.flags
                // Prevent NavController from recreating activity if its intent doesn't have FLAG_ACTIVITY_CLEAR_TASK
                if ((flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0 && (flags and Intent.FLAG_ACTIVITY_CLEAR_TASK) == 0) {
                    Timber.w("createDeepLinkIntent: add FLAG_ACTIVITY_CLEAR_TASK")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            }
    }

    fun acceptDragStartEvent(clipDescription: ClipDescription): Boolean {
        Timber.i("Drag start event mime types = ${clipDescription.mimeTypes()}")
        return clipDescription.hasMimeType(TORRENT_FILE_MIME_TYPE) ||
                TORRENT_LINK_MIME_TYPES.any(clipDescription::hasMimeType)
    }

    fun getAddTorrentDirections(clipData: ClipData): AddTorrentDirections? {
        return clipData.getTorrentUri(getApplication())?.let(::getAddTorrentDirections)
    }

    private fun getAddTorrentDirections(uri: TorrentUri): AddTorrentDirections {
        return when (uri.type) {
            TorrentUri.Type.File -> AddTorrentDirections(
                R.id.add_torrent_file_fragment,
                AddTorrentFileFragmentArgs(uri.uri).toBundle()
            )

            TorrentUri.Type.Link -> AddTorrentDirections(
                R.id.add_torrent_link_fragment,
                AddTorrentLinkFragmentArgs(uri.uri).toBundle()
            )
        }
    }

    private val _showRpcErrorToast = MutableStateFlow<GlobalRpcClient.BackgroundRpcRequestError?>(null)
    val showRpcErrorToast: StateFlow<GlobalRpcClient.BackgroundRpcRequestError?> by ::_showRpcErrorToast
    fun rpcErrorToastShown() {
        _showRpcErrorToast.value = null
    }

    init {
        viewModelScope.launch {
            GlobalRpcClient.backgroundRpcRequestsErrors.receiveAsFlow().collect(_showRpcErrorToast)
        }
    }
}
