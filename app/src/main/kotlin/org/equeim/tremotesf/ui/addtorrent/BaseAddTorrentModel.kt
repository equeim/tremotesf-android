// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.normalizePath
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.getFreeSpaceInDirectory
import org.equeim.tremotesf.rpc.requests.getTorrentsLabels
import org.equeim.tremotesf.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber

abstract class BaseAddTorrentModel(application: Application) : AndroidViewModel(application) {
    var shouldSetInitialLocalInputs = true
    var shouldSetInitialRpcInputs = true

    data class InitialRpcInputs(
        val downloadingServerSettings: DownloadingServerSettings,
        val allLabels: Set<String>,
    )

    val initialRpcInputs: StateFlow<RpcRequestState<InitialRpcInputs>> =
        GlobalRpcClient.performRecoveringRequest {
            coroutineScope {
                val settings = async { getDownloadingServerSettings() }
                val labels = async { getTorrentsLabels() }
                InitialRpcInputs(settings.await(), labels.await())
            }
        }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialRpcInputs = true }
            .stateIn(GlobalRpcClient, viewModelScope)

    val shouldShowLabels: StateFlow<Boolean> = GlobalRpcClient.serverCapabilitiesFlow.map {
        it?.supportsLabels != false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    suspend fun getInitialDownloadDirectory(settings: DownloadingServerSettings): String {
        return if (Settings.rememberAddTorrentParameters.get()) {
            GlobalServers.serversState.value.currentServer
                ?.lastDownloadDirectory
                ?.takeIf { it.isNotEmpty() }
                ?.normalizePath(GlobalRpcClient.serverCapabilities)
                ?: settings.downloadDirectory
        } else {
            settings.downloadDirectory
        }.toNativeSeparators()
    }

    suspend fun getInitialStartAfterAdding(settings: DownloadingServerSettings): Boolean =
        if (Settings.rememberAddTorrentParameters.get()) {
            when (Settings.lastAddTorrentStartAfterAdding.get()) {
                Settings.StartTorrentAfterAdding.Start -> true
                Settings.StartTorrentAfterAdding.DontStart -> false
                Settings.StartTorrentAfterAdding.Unknown -> settings.startAddedTorrents
            }
        } else {
            settings.startAddedTorrents
        }

    suspend fun getInitialPriority(): TorrentLimits.BandwidthPriority =
        if (Settings.rememberAddTorrentParameters.get()) {
            Settings.lastAddTorrentPriority.get()
        } else {
            TorrentLimits.BandwidthPriority.Normal
        }

    suspend fun getInitialLabels(): Set<String> = if (Settings.rememberAddTorrentParameters.get()) {
        Settings.lastAddTorrentLabels.get()
    } else {
        emptySet()
    }

    suspend fun getFreeSpace(directory: String): FileSize? = try {
        GlobalRpcClient.getFreeSpaceInDirectory(directory)
    } catch (e: RpcRequestError) {
        Timber.e(e, "Failed to get free space for directory $directory")
        null
    }
}
