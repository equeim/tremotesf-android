// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PlaceholderLayoutBinding
import org.equeim.tremotesf.databinding.TrackersFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.rpc.RpcRequestState
import org.equeim.tremotesf.torrentfile.rpc.performPeriodicRequest
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.Tracker
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.addTorrentTrackers
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.getTorrentTrackers
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.removeTorrentTrackers
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.replaceTorrentTracker
import org.equeim.tremotesf.torrentfile.rpc.stateIn
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.hide
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.showError
import org.equeim.tremotesf.ui.utils.showLoading
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


class TrackersFragment : TorrentPropertiesFragment.PagerFragment(
    R.layout.trackers_fragment,
    TorrentPropertiesFragment.PagerAdapter.Tab.Trackers
) {
    private val torrentPropertiesFragment: TorrentPropertiesFragment
        get() = requireParentFragment() as TorrentPropertiesFragment

    private val model by viewModels<TrackersFragmentViewModel> {
        viewModelFactory {
            initializer { TrackersFragmentViewModel(TorrentPropertiesFragment.getTorrentHashString(navController)) }
        }
    }

    private val binding by viewLifecycleObject(TrackersFragmentBinding::bind)
    private val trackersAdapter by viewLifecycleObject { TrackersAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parent = requireParentFragment()
        parent.setFragmentResultListener(AddTrackersDialogFragment::class.qualifiedName!!) { _, bundle ->
            model.addTrackers(AddTrackersDialogFragment.Result.fromBundle(bundle).announceUrls)
        }
        parent.setFragmentResultListener(EditTrackerDialogFragment::class.qualifiedName!!) { _, bundle ->
            val result = EditTrackerDialogFragment.Result.fromBundle(bundle)
            model.replaceTracker(result.trackerId, result.newAnnounceUrl)
        }
        parent.setFragmentResultListener(RemoveTrackersDialogFragment::class.qualifiedName!!) { _, bundle ->
            model.removeTrackers(RemoveTrackersDialogFragment.Result.fromBundle(bundle).trackerIds.asList())
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        binding.trackersView.apply {
            adapter = trackersAdapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    torrentPropertiesFragment.binding.fab.apply {
                        if (dy > 0) {
                            hide()
                        } else if (dy < 0) {
                            show()
                        }
                    }
                }
            })
        }

        model.trackers.launchAndCollectWhenStarted(viewLifecycleOwner) {
            when (it) {
                is RpcRequestState.Loaded -> {
                    val peers = it.response
                    when {
                        peers == null -> showPlaceholder { showError(getText(R.string.torrent_not_found)) }
                        peers.isEmpty() -> showPlaceholder { showError(getText(R.string.no_trackers)) }
                        else -> showTrackers(peers)
                    }
                }

                is RpcRequestState.Loading -> showPlaceholder { showLoading() }
                is RpcRequestState.Error -> showPlaceholder { showError(it.error) }
            }
        }
    }

    private suspend inline fun showPlaceholder(show: PlaceholderLayoutBinding.() -> Unit) {
        binding.placeholderView.show()
        trackersAdapter.update(null)
    }

    private suspend fun showTrackers(peers: List<Tracker>) {
        binding.placeholderView.hide()
        trackersAdapter.update(peers)
    }
}

class TrackersFragmentViewModel(private val torrentHashString: String) : ViewModel() {
    private val refreshRequests = MutableSharedFlow<Unit>()
    val trackers: StateFlow<RpcRequestState<List<Tracker>?>> = GlobalRpcClient.performPeriodicRequest(refreshRequests) {
        getTorrentTrackers(torrentHashString)
    }.stateIn(GlobalRpcClient, viewModelScope)

    fun addTrackers(announceUrls: List<String>) {
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.trackers_add_error) {
                addTorrentTrackers(
                    torrentHashString,
                    announceUrls
                )
            }
            if (ok) {
                refreshRequests.emit(Unit)
            }
        }
    }

    fun replaceTracker(trackerId: Int, newAnnounceUrl: String) {
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.tracker_replace_error) {
                replaceTorrentTracker(
                    torrentHashString,
                    trackerId,
                    newAnnounceUrl
                )
            }
            if (ok) {
                refreshRequests.emit(Unit)
            }
        }
    }

    fun removeTrackers(trackerIds: List<Int>) {
        viewModelScope.launch {
            val ok = GlobalRpcClient.awaitBackgroundRpcRequest(R.string.trackers_remove_error) {
                removeTorrentTrackers(
                    torrentHashString,
                    trackerIds
                )
            }
            if (ok) {
                refreshRequests.emit(Unit)
            }
        }
    }
}
