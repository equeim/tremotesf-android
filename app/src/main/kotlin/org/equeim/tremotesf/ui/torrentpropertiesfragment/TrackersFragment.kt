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

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.databinding.TrackersFragmentBinding
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted


class TrackersFragment : TorrentPropertiesFragment.PagerFragment(R.layout.trackers_fragment, TorrentPropertiesFragment.PagerAdapter.Tab.Trackers) {
    private val torrentPropertiesFragment: TorrentPropertiesFragment
        get() = requireParentFragment() as TorrentPropertiesFragment

    private val binding by viewBinding(TrackersFragmentBinding::bind)
    private var trackersAdapter: TrackersAdapter? = null

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val trackersAdapter = TrackersAdapter(this)
        this.trackersAdapter = trackersAdapter

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

        val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(navController)
        propertiesFragmentModel.torrent.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    override fun onDestroyView() {
        trackersAdapter = null
        super.onDestroyView()
    }

    private fun update(torrent: Torrent?) {
        trackersAdapter?.let { adapter ->
            adapter.update(torrent)
            binding.placeholder.visibility = if ((adapter.itemCount == 0) && torrent != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
