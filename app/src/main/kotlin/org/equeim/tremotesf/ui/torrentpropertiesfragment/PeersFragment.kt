/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.databinding.PeersFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.torrentpropertiesfragment.TorrentPropertiesFragmentViewModel.Companion.hasTorrent
import org.equeim.tremotesf.ui.utils.*
import timber.log.Timber


data class Peer(
    val address: String,
    val client: String,
    var downloadSpeed: Long,
    var uploadSpeed: Long,
    var progress: Double
) {
    constructor(peer: org.equeim.libtremotesf.Peer) : this(
        peer.address,
        peer.client,
        peer.downloadSpeed,
        peer.uploadSpeed,
        peer.progress
    )

    fun updatedFrom(peer: org.equeim.libtremotesf.Peer): Peer {
        return this.copy(
            downloadSpeed = peer.downloadSpeed,
            uploadSpeed = peer.uploadSpeed,
            progress = peer.progress
        )
    }
}

class PeersFragment : TorrentPropertiesFragment.PagerFragment(R.layout.peers_fragment, TorrentPropertiesFragment.PagerAdapter.Tab.Peers) {
    private val binding by viewBinding(PeersFragmentBinding::bind)
    private var peersAdapter: PeersAdapter? = null

    private val model: Model by viewModels { viewModelFactory { Model(TorrentPropertiesFragmentViewModel.get(this).torrent) } }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val peersAdapter = PeersAdapter()
        this.peersAdapter = peersAdapter

        binding.peersView.apply {
            adapter = peersAdapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.peers.collectWhenStarted(viewLifecycleOwner, peersAdapter::update)

        combine(model.torrent.hasTorrent(), model.peers, model.loaded, ::Triple)
            .collectWhenStarted(viewLifecycleOwner) { (torrent, peers, loaded) ->
                updatePlaceholder(torrent, peers, loaded)
            }
    }

    override fun onToolbarClicked() {
        binding.peersView.scrollToPosition(0)
    }

    override fun onDestroyView() {
        peersAdapter = null
        super.onDestroyView()
    }

    override fun onNavigatedFromParent() {
        model.destroy()
    }

    private fun updatePlaceholder(hasTorrent: Boolean, peers: List<Peer>, loaded: Boolean) = with(binding) {
        if (!hasTorrent) {
            progressBar.visibility = View.GONE
            placeholder.visibility = View.GONE
        } else {
            if (loaded) {
                progressBar.visibility = View.GONE
                placeholder.visibility = if (peers.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            } else {
                progressBar.visibility = View.VISIBLE
            }
        }
    }

    private class Model(val torrent: StateFlow<Torrent?>) : ViewModel() {
        private val _peers = MutableStateFlow<List<Peer>>(emptyList())
        val peers: StateFlow<List<Peer>> by ::_peers

        private val _loaded = MutableStateFlow(false)
        val loaded: StateFlow<Boolean> by ::_loaded

        init {
            Timber.i("constructor called")

            torrent.hasTorrent().onEach {
                if (it) {
                    Timber.i("Torrent appeared, setting peersEnabled to true")
                    torrent.value?.peersEnabled = true
                } else {
                    Timber.i("Torrent appeared, resetting")
                    reset()
                }
            }.launchIn(viewModelScope)

            viewModelScope.launch { GlobalRpc.torrentPeersUpdatedEvents.collect(::onTorrentPeersUpdated) }
        }

        override fun onCleared() {
            Timber.i("onCleared() called")
            destroy()
        }

        fun destroy() {
            Timber.i("destroy() called")
            viewModelScope.cancel()
            reset()
        }

        private fun reset() {
            Timber.i("reset() called")
            torrent.value?.peersEnabled = false
            _peers.value = emptyList()
            _loaded.value = false
        }

        private fun onTorrentPeersUpdated(data: Rpc.TorrentPeersUpdatedData) {
            val (torrentId, removed, changed, added) = data

            if (torrentId != torrent.value?.id) return

            if (loaded.value && removed.isEmpty() && changed.isEmpty() && added.isEmpty()) {
                return
            }

            val peers = this.peers.value.toMutableList()

            for (index in removed) {
                peers.removeAt(index)
            }

            if (changed.isNotEmpty()) {
                val changedIter = changed.iterator()
                var changedPeer = changedIter.next()
                var changedPeerAddress = changedPeer.address
                val peersIter = peers.listIterator()
                while (peersIter.hasNext()) {
                    val peer = peersIter.next()
                    if (peer.address == changedPeerAddress) {
                        peersIter.set(peer.updatedFrom(changedPeer))
                        if (changedIter.hasNext()) {
                            changedPeer = changedIter.next()
                            changedPeerAddress = changedPeer.address
                        } else {
                            break
                        }
                    }
                }
            }

            for (peer in added) {
                peers.add(Peer(peer))
            }

            _loaded.value = true
            _peers.value = peers
        }
    }
}