/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

class TorrentPropertiesFragmentViewModel(val args: TorrentPropertiesFragmentArgs, savedStateHandle: SavedStateHandle) : ViewModel() {
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
                if (torrent != null) {
                    Timber.i("Torrent appeared")
                } else {
                    Timber.i("Torrent disappeared")
                }
                _torrent.value = torrent
            }
            .launchIn(viewModelScope)
    }

    companion object {
        fun get(fragment: Fragment): TorrentPropertiesFragmentViewModel {
            val entry = fragment.navController.getBackStackEntry(R.id.torrent_properties_fragment)
            return ViewModelProvider(entry, fragment.navArgsViewModelFactory(
                entry.arguments,
                TorrentPropertiesFragmentArgs::fromBundle
            ) { args, _, handle -> TorrentPropertiesFragmentViewModel(args, handle) })[TorrentPropertiesFragmentViewModel::class.java]
        }

        fun lazy(fragment: Fragment) = fragment.navGraphViewModels<TorrentPropertiesFragmentViewModel>(R.id.torrent_properties_fragment) {
            fragment.navArgsViewModelFactory(
                R.id.torrent_properties_fragment,
                TorrentPropertiesFragmentArgs::fromBundle
            ) { args, _, handle -> TorrentPropertiesFragmentViewModel(args, handle) }
        }

        fun StateFlow<Torrent?>.hasTorrent() = map { it != null }.distinctUntilChanged()
    }
}
