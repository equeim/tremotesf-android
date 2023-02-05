// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.*
import timber.log.Timber

class TorrentPropertiesFragmentViewModel(
    val args: TorrentPropertiesFragmentArgs,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    var rememberedPagerItem: Int by savedState(savedStateHandle, -1)

    private val _torrent = MutableStateFlow<Torrent?>(null)
    val torrent: StateFlow<Torrent?> by ::_torrent

    val showTorrentRemovedMessage = MutableStateFlow(false)

    init {
        GlobalRpc.torrents
            .map { torrents -> torrents.find { it.hashString == args.hash } }
            .distinctUntilChanged()
            .onEach { torrent ->
                if (torrent == null && _torrent.value != null && GlobalRpc.isConnected.value) {
                    showTorrentRemovedMessage.value = true
                }
                if ((torrent == null) != (_torrent.value == null)) {
                    if (torrent != null) {
                        Timber.i("Torrent appeared")
                    } else {
                        Timber.i("Torrent disappeared")
                    }
                } else {
                    Timber.i("Torrent changed")
                }
                _torrent.value = torrent
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun get(navController: NavController): TorrentPropertiesFragmentViewModel {
            val entry = navController.getBackStackEntry(R.id.torrent_properties_fragment)
            val factory = viewModelFactory {
                initializer {
                    val args = TorrentPropertiesFragmentArgs.fromBundle(checkNotNull(entry.arguments))
                    TorrentPropertiesFragmentViewModel(args, createSavedStateHandle())
                }
            }
            return ViewModelProvider(entry, factory)[TorrentPropertiesFragmentViewModel::class.java]
        }

        fun lazy(fragment: Fragment) = fragment.navGraphViewModels<TorrentPropertiesFragmentViewModel>(R.id.torrent_properties_fragment) {
            viewModelFactory {
                initializer {
                    val entry = fragment.navController.getBackStackEntry(R.id.torrent_properties_fragment)
                    val args = TorrentPropertiesFragmentArgs.fromBundle(checkNotNull(entry.arguments))
                    TorrentPropertiesFragmentViewModel(args, createSavedStateHandle())
                }
            }
        }

        fun StateFlow<Torrent?>.hasTorrent() = map { it != null }.distinctUntilChanged()
    }
}
