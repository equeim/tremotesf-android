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

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TrackersFragmentBinding
import org.equeim.tremotesf.utils.viewBinding


class TrackersFragment : TorrentPropertiesFragment.PagerFragment(R.layout.trackers_fragment) {
    private val torrentPropertiesFragment: TorrentPropertiesFragment
        get() = requireParentFragment() as TorrentPropertiesFragment

    private val binding by viewBinding(TrackersFragmentBinding::bind)
    private var trackersAdapter: TrackersAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val trackersAdapter = TrackersAdapter(torrentPropertiesFragment)
        this.trackersAdapter = trackersAdapter

        binding.trackersView.apply {
            adapter = trackersAdapter
            layoutManager = LinearLayoutManager(activity)
            addItemDecoration(DividerItemDecoration(activity,
                                                                  DividerItemDecoration.VERTICAL))
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

        update()
        trackersAdapter.selectionTracker.restoreInstanceState(savedInstanceState)
    }

    override fun onDestroyView() {
        trackersAdapter = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        trackersAdapter?.selectionTracker?.saveInstanceState(outState)
    }

    override fun update() {
        trackersAdapter?.let { adapter ->
            val torrent = torrentPropertiesFragment.torrent
            adapter.update(torrent)
            binding.placeholder.visibility = if ((adapter.itemCount == 0) && torrent != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}