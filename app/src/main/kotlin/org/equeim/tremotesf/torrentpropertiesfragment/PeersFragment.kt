/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.TorrentWrapper
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

    private val model: Model by viewModels()

    private var torrent: TorrentWrapper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        update()
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

        peersAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                if (itemCount == 0) updatePlaceholder()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (itemCount == peersAdapter.itemCount) updatePlaceholder()
            }
        })

        model.peers.observe(viewLifecycleOwner, ::updateAdapter)
    }

    override fun onDestroyView() {
        peersAdapter = null
        super.onDestroyView()
    }

    override fun update() {
        model.torrent = (requireParentFragment() as TorrentPropertiesFragment).torrent
    }

    private fun updateAdapter(peers: List<Peer>) {
        val peersAdapter = this.peersAdapter ?: return
        peersAdapter.update(peers)
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        val peersAdapter = this.peersAdapter ?: return
        val torrent = this.torrent
        if (torrent == null) {
            progress_bar.visibility = View.GONE
        } else {
            if (torrent.peersLoaded) {
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

    class Model : ViewModel() {
        var torrent: TorrentWrapper? = null
            set(value) {
                if (value != field) {
                    field = value
                    if (value == null) {
                        reset()
                    } else {
                        value.peersEnabled = true
                    }
                }
            }

        val peers = NonNullMutableLiveData<List<Peer>>(emptyList())

        init {
            Rpc.torrentPeersUpdatedEvent.observeForever(::onTorrentPeersUpdated)
        }

        private fun reset() {
            peers.value = emptyList()
        }

        private fun onTorrentPeersUpdated(data: Rpc.TorrentPeersUpdatedData) {
            val (torrentId, changed, added, removed) = data

            if (torrentId != torrent?.id) return

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
                            changedPeerAddress = ""
                        }
                    }
                }
            }

            for (peer in added) {
                peers.add(Peer(peer))
            }

            this.peers.value = peers
        }

        override fun onCleared() {
            Rpc.torrentPeersUpdatedEvent.removeObserver(::onTorrentPeersUpdated)
        }
    }
}