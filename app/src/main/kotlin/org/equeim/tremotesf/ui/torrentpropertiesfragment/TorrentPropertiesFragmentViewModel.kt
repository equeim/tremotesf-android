/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.savedState
import org.equeim.tremotesf.ui.utils.savedStateViewModelFactory
import timber.log.Timber

class TorrentPropertiesFragmentViewModel(val hashString: String, savedStateHandle: SavedStateHandle) : ViewModel() {
    var rememberedPagerItem: Int by savedState(savedStateHandle, -1)

    private val _torrent = MutableStateFlow<Torrent?>(null)
    val torrent: StateFlow<Torrent?> by ::_torrent

    val showTorrentRemovedMessage = MutableStateFlow(false)

    init {
        GlobalRpc.torrents
            .map { torrents -> torrents.find { it.hashString == hashString } }
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
        fun getLazy(currentFragment: Fragment): Lazy<TorrentPropertiesFragmentViewModel> {
            val entry by lazy {
                currentFragment.navController.getBackStackEntry(R.id.torrent_properties_fragment)
            }
            return currentFragment.createViewModelLazy(
                TorrentPropertiesFragmentViewModel::class,
                storeProducer = { entry.viewModelStore },
                factoryProducer = { currentFragment.factory(entry) }
            )
        }

        fun get(currentFragment: Fragment): TorrentPropertiesFragmentViewModel {
            val entry = currentFragment.navController.getBackStackEntry(R.id.torrent_properties_fragment)
            return ViewModelProvider(
                entry,
                currentFragment.factory(entry)
            )[TorrentPropertiesFragmentViewModel::class.java]
        }

        private fun Fragment.factory(entry: NavBackStackEntry): ViewModelProvider.Factory {
            return savedStateViewModelFactory { _, savedStateHandle ->
                val args = TorrentPropertiesFragmentArgs.fromBundle(checkNotNull(entry.arguments))
                TorrentPropertiesFragmentViewModel(args.hash, savedStateHandle)
            }
        }

        fun StateFlow<Torrent?>.hasTorrent() = map { it != null }.distinctUntilChanged()
    }
}
