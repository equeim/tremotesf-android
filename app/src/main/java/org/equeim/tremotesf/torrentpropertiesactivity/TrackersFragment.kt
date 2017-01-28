/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.torrentpropertiesactivity

import android.app.Fragment
import android.os.Bundle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

import org.equeim.tremotesf.R


class TrackersFragment : Fragment() {
    private lateinit var activity: TorrentPropertiesActivity
    private lateinit var trackersAdapter: TrackersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = getActivity() as TorrentPropertiesActivity
        trackersAdapter = TrackersAdapter(activity)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.trackers_fragment, container, false)

        val trackersView = view.findViewById(R.id.trackers_view) as RecyclerView
        trackersView.adapter = trackersAdapter
        trackersView.layoutManager = LinearLayoutManager(activity)
        trackersView.addItemDecoration(DividerItemDecoration(activity,
                                                             DividerItemDecoration.VERTICAL))
        (trackersView.itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false

        trackersView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                if (dy > 0) {
                    activity.fab.hide()
                } else if (dy < 0) {
                    activity.fab.show()
                }
            }
        })

        if (savedInstanceState != null) {
            update()
            trackersAdapter.selector.restoreInstanceState(savedInstanceState)
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        update()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        trackersAdapter.selector.saveInstanceState(outState)
    }

    fun update() {
        if (isAdded) {
            trackersAdapter.update()
        }
    }
}