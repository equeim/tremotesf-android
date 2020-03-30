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

package org.equeim.tremotesf.torrentpropertiesfragment

import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.utils.NonNullMutableLiveData

import kotlinx.android.synthetic.main.peers_fragment.*


data class Peer(val address: String,
                val client: String,
                var downloadSpeed: Long,
                var uploadSpeed: Long,
                var progress: Double) {
    constructor(peer: org.equeim.libtremotesf.Peer) : this(peer.address,
                                                           peer.client,
                                                           peer.downloadSpeed,
                                                           peer.uploadSpeed,
                                                           peer.progress)

    fun updatedFrom(peer: org.equeim.libtremotesf.Peer): Peer {
        return this.copy(downloadSpeed = peer.downloadSpeed,
                         uploadSpeed = peer.uploadSpeed,
                         progress = peer.progress)
    }
}

class PeersFragment : Fragment(R.layout.peers_fragment), TorrentPropertiesFragment.PagerFragment {
    private var peersAdapter: PeersAdapter? = null

    private lateinit var model: Model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = requireParentFragment() as TorrentPropertiesFragment
        model = ViewModelProvider(parent, ModelFactory(parent.torrent))[Model::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val peersAdapter = PeersAdapter()
        this.peersAdapter = peersAdapter

        peers_view.adapter = peersAdapter
        peers_view.layoutManager = LinearLayoutManager(activity)
        peers_view.addItemDecoration(DividerItemDecoration(activity,
                DividerItemDecoration.VERTICAL))
        peers_view.itemAnimator = null

        model.peers.observe(viewLifecycleOwner, ::updateAdapter)
    }

    override fun onDestroyView() {
        peersAdapter = null
        super.onDestroyView()
    }

    override fun update() {
        model.torrent = (requireParentFragment() as TorrentPropertiesFragment).torrent
    }

    override fun onNavigatedFrom() {
        model.peers.removeObservers(viewLifecycleOwner)
        model.torrent = null
    }

    private fun updateAdapter(peers: List<Peer>) {
        val peersAdapter = this.peersAdapter ?: return
        val wasEmpty = (peersAdapter.itemCount == 0)
        if (wasEmpty && peers.isEmpty()) {
            updatePlaceholder(peersAdapter)
            return
        }
        peersAdapter.update(peers)
        if (wasEmpty || peers.isEmpty()) {
            updatePlaceholder(peersAdapter)
        }
    }

    private fun updatePlaceholder(peersAdapter: PeersAdapter) {
        if (model.torrent == null) {
            progress_bar.visibility = View.GONE
        } else {
            if (model.loaded) {
                progress_bar.visibility = View.GONE
                placeholder.visibility = if (peersAdapter.itemCount == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            } else {
                progress_bar.visibility = View.VISIBLE
            }
        }
    }

    private class ModelFactory(private val torrent: Torrent?) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == Model::class.java) {
                @Suppress("UNCHECKED_CAST")
                return Model(torrent) as T
            }
            throw IllegalArgumentException()
        }
    }

    private class Model(torrent: Torrent?) : ViewModel() {
        var torrent: Torrent? = null
            set(value) {
                if (value != field) {
                    val oldValue = field
                    field = value
                    if (value == null) {
                        oldValue?.peersEnabled = false
                        reset()
                    } else {
                        value.peersEnabled = true
                    }
                }
            }

        val peers = NonNullMutableLiveData<List<Peer>>(emptyList())
        var loaded = false
            private set

        private val peersUpdatedObserver = Observer(::onTorrentPeersUpdated)

        init {
            this.torrent = torrent
            Rpc.torrentPeersUpdatedEvent.observeForever(peersUpdatedObserver)
        }

        private fun reset() {
            peers.value = emptyList()
        }

        private fun onTorrentPeersUpdated(data: Rpc.TorrentPeersUpdatedData) {
            val (torrentId, removed, changed, added) = data

            if (torrentId != torrent?.id) return

            if (loaded && removed.isEmpty() && changed.isEmpty() && added.isEmpty()) {
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

            loaded = true

            this.peers.value = peers
        }

        override fun onCleared() {
            Rpc.torrentPeersUpdatedEvent.removeObserver(peersUpdatedObserver)
        }
    }
}