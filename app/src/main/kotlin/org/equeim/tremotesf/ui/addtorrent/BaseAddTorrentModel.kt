package org.equeim.tremotesf.ui.addtorrent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestError
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.normalizePath
import org.equeim.tremotesf.torrentfile.rpc.performRecoveringRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.FileSize
import org.equeim.tremotesf.torrentfile.rpc.requests.getFreeSpaceInDirectory
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.DownloadingServerSettings
import org.equeim.tremotesf.torrentfile.rpc.requests.serversettings.getDownloadingServerSettings
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.torrentfile.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.Settings
import timber.log.Timber

abstract class BaseAddTorrentModel(application: Application) : AndroidViewModel(application) {
    var shouldSetInitialLocalInputs = true
    var shouldSetInitialRpcInputs = true

    val downloadingSettings: StateFlow<RpcRequestState<DownloadingServerSettings>> =
        GlobalRpcClient.performRecoveringRequest { getDownloadingServerSettings() }
            .onEach { if (it !is RpcRequestState.Loaded) shouldSetInitialRpcInputs = true }
            .stateIn(GlobalRpcClient, viewModelScope)

    suspend fun getInitialDownloadDirectory(settings: DownloadingServerSettings): String {
        return if (Settings.rememberDownloadDirectory.get()) {
            GlobalServers.serversState.value.currentServer
                ?.lastDownloadDirectory
                ?.takeIf { it.isNotEmpty() }
                ?.normalizePath(GlobalRpcClient.serverCapabilities)
                ?: settings.downloadDirectory
        } else {
            settings.downloadDirectory
        }.toNativeSeparators()
    }

    suspend fun getFreeSpace(directory: String): FileSize? = try {
        GlobalRpcClient.getFreeSpaceInDirectory(directory)
    } catch (e: RpcRequestError) {
        Timber.e("Failed to get free space for directory $directory")
        null
    }
}
