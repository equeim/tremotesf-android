// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performPeriodicRequest
import org.equeim.tremotesf.rpc.requests.startTorrents
import org.equeim.tremotesf.rpc.requests.startTorrentsNow
import org.equeim.tremotesf.rpc.requests.stopTorrents
import org.equeim.tremotesf.rpc.requests.torrentproperties.TorrentDetails
import org.equeim.tremotesf.rpc.requests.torrentproperties.getTorrentDetails
import org.equeim.tremotesf.rpc.requests.torrentproperties.renameTorrentFile
import org.equeim.tremotesf.rpc.requests.verifyTorrents
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.savedState
import timber.log.Timber

class TorrentPropertiesFragmentViewModel(
    val args: TorrentPropertiesFragmentArgs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var rememberedPagerItem: Int by savedState(savedStateHandle, -1)

    private val refreshRequests = MutableSharedFlow<Unit>()
    val torrentDetails: StateFlow<RpcRequestState<TorrentDetails?>> =
        GlobalRpcClient.performPeriodicRequest(refreshRequests) {
            getTorrentDetails(args.torrentHashString)
        }.stateIn(GlobalRpcClient, viewModelScope)

    data class TorrentFileRenamed(
        val filePath: String,
        val newName: String,
    )

    private val _torrentFileRenamedEvents = MutableSharedFlow<TorrentFileRenamed>()
    val torrentFileRenamedEvents by ::_torrentFileRenamedEvents

    fun renameTorrentFile(filePath: String, newName: String) {
        Timber.d("renameTorrentFile() called with: filePath = $filePath, newName = $newName")
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.file_rename_error) {
                renameTorrentFile(
                    args.torrentHashString,
                    filePath,
                    newName
                )
            }
            if (ok) {
                _torrentFileRenamedEvents.emit(TorrentFileRenamed(filePath, newName))
                refreshRequests.emit(Unit)
            }
        }
    }

    fun startTorrent(torrentId: Int, now: Boolean) {
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_start_error) {
                if (now) {
                    startTorrentsNow(listOf(torrentId))
                } else {
                    startTorrents(listOf(torrentId))
                }
            }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    fun pauseTorrent(torrentId: Int) {
        viewModelScope.launch {
            val ok =
                GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_pause_error) { stopTorrents(listOf(torrentId)) }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    fun checkTorrent(torrentId: Int) {
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.torrents_check_error) {
                verifyTorrents(
                    listOf(torrentId)
                )
            }
            if (ok) refreshRequests.emit(Unit)
        }
    }

    companion object {
        fun from(fragment: Fragment): Lazy<TorrentPropertiesFragmentViewModel> =
            fragment.navGraphViewModels(R.id.torrent_properties_fragment) {
                viewModelFactory {
                    initializer {
                        val entry = fragment.navController.getBackStackEntry(R.id.torrent_properties_fragment)
                        val args = TorrentPropertiesFragmentArgs.fromBundle(checkNotNull(entry.arguments))
                        TorrentPropertiesFragmentViewModel(args, createSavedStateHandle())
                    }
                }
            }
    }
}
