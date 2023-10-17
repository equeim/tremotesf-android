// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.databinding.WebSeedersFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.getTorrentWebSeeders
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject

class WebSeedersFragment : TorrentPropertiesFragment.PagerFragment(
    R.layout.web_seeders_fragment,
    TorrentPropertiesFragment.PagerAdapter.Tab.WebSeeders
) {
    private val model by viewModels<WebSeedersFragmentViewModel> {
        viewModelFactory {
            initializer {
                WebSeedersFragmentViewModel(
                    TorrentPropertiesFragment.getTorrentHashString(
                        navController
                    )
                )
            }
        }
    }
    private val binding by viewLifecycleObject(WebSeedersFragmentBinding::bind)
    private val adapter by viewLifecycleObject { WebSeedersAdapter() }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        binding.webSeedersView.apply {
            adapter = this@WebSeedersFragment.adapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
        }

        model.webSeeders.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> {
                    val webSeeders = it.response
                    when {
                        webSeeders == null -> showPlaceholder { showError(getText(R.string.torrent_not_found)) }
                        webSeeders.isEmpty() -> showPlaceholder { showError(getText(R.string.no_web_seeders)) }
                        else -> showWebSeeders(webSeeders)
                    }
                }
                is RpcRequestState.Loading -> showPlaceholder { showLoading() }
                is RpcRequestState.Error -> showPlaceholder { showError(it.error) }
            }
        }
    }

    private inline fun showPlaceholder(show: PlaceholderLayoutBinding.() -> Unit) {
        binding.placeholderView.show()
        adapter.update(null)
    }

    private fun showWebSeeders(webSeeders: List<String>) {
        binding.placeholderView.hide()
        adapter.update(webSeeders)
    }
}

private class WebSeedersAdapter : AsyncLoadingListAdapter<String, WebSeedersAdapter.ViewHolder>(Callback()) {
    private val comparator = AlphanumericComparator()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.web_seeders_item, parent, false) as TextView
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getItem(position)
    }

    fun update(webSeeders: List<String>?) {
        submitList(webSeeders?.sortedWith(comparator))
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private class Callback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = true
    }
}

private class WebSeedersFragmentViewModel(torrentHashString: String) : ViewModel() {
    val webSeeders: StateFlow<RpcRequestState<List<String>?>> = GlobalRpcClient.performPeriodicRequest {
        getTorrentWebSeeders(torrentHashString)
    }.stateIn(GlobalRpcClient, viewModelScope)
}
