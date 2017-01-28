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
import android.widget.TextView

import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Torrent


class PeersFragment : Fragment() {
    private lateinit var activity: TorrentPropertiesActivity

    private var placeholder: TextView? = null
    private var progressBar: View? = null

    private var peersAdapter: PeersAdapter? = null

    private var torrent: Torrent? = null
        set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    value.peersUpdateEnabled = true
                    value.peersLoadedListener = { update() }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = getActivity() as TorrentPropertiesActivity
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.peers_fragment, container, false)

        placeholder = view.findViewById(R.id.placeholder) as TextView
        progressBar = view.findViewById(R.id.progress_bar)

        peersAdapter = PeersAdapter(activity)

        val peersView = view.findViewById(R.id.peers_view) as RecyclerView
        peersView.adapter = peersAdapter
        peersView.layoutManager = LinearLayoutManager(activity)
        peersView.addItemDecoration(DividerItemDecoration(activity,
                                                          DividerItemDecoration.VERTICAL))
        peersView.itemAnimator = null

        return view
    }

    override fun onStart() {
        super.onStart()
        update()
        torrent?.peersLoadedListener = { update() }
    }

    override fun onStop() {
        super.onStop()
        torrent?.peersLoadedListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        placeholder = null
        progressBar = null
        peersAdapter = null
    }

    fun update() {
        if (isAdded) {
            torrent = activity.torrent
            peersAdapter!!.update()

            if (torrent?.peers == null) {
                progressBar!!.visibility = View.VISIBLE
            } else {
                progressBar!!.visibility = View.GONE
                placeholder!!.visibility = if (peersAdapter!!.itemCount == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }
}