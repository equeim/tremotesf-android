// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PeersFragmentBinding
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.Peer
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.getTorrentPeers
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject

class PeersFragment :
    TorrentPropertiesFragment.PagerFragment(R.layout.peers_fragment, TorrentPropertiesFragment.PagerAdapter.Tab.Peers) {
    private val model: PeersFragmentViewModel by viewModels {
        viewModelFactory {
            initializer {
                PeersFragmentViewModel(TorrentPropertiesFragment.getTorrentHashString(navController))
            }
        }
    }

    private val binding by viewLifecycleObject(PeersFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val peersAdapter = PeersAdapter()
        binding.peersView.apply {
            adapter = peersAdapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.peers.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> {
                    val peers = it.response
                    when {
                        peers == null -> showPlaceholder(peersAdapter) { showError(getText(R.string.torrent_not_found)) }
                        peers.isEmpty() -> showPlaceholder(peersAdapter) { showError(getText(R.string.no_peers)) }
                        else -> showPeers(peers, peersAdapter)
                    }
                }

                is RpcRequestState.Loading -> showPlaceholder(peersAdapter) { showLoading() }
                is RpcRequestState.Error -> showPlaceholder(peersAdapter) { showError(it.error) }
            }
        }
    }

    private inline fun showPlaceholder(adapter: PeersAdapter, show: PlaceholderLayoutBinding.() -> Unit) {
        binding.placeholderView.show()
        adapter.update(null)
    }

    private fun showPeers(peers: List<Peer>, adapter: PeersAdapter) {
        binding.placeholderView.hide()
        adapter.update(peers)
    }

    override fun onToolbarClicked() {
        binding.peersView.scrollToPosition(0)
    }
}

private class PeersFragmentViewModel(torrentHashString: String) : ViewModel() {
    val peers: StateFlow<RpcRequestState<List<Peer>?>> =
        GlobalRpcClient.performPeriodicRequest { getTorrentPeers(torrentHashString) }
            .stateIn(GlobalRpcClient, viewModelScope)
}
