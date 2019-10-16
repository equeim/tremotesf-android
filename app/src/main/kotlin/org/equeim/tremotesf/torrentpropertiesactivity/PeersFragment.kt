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

package org.equeim.tremotesf.torrentpropertiesactivity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.TorrentData

import org.equeim.tremotesf.setPeersEnabled

import kotlinx.android.synthetic.main.peers_fragment.*


class PeersFragment : Fragment(R.layout.peers_fragment) {
    private lateinit var activity: TorrentPropertiesActivity

    private var peersAdapter: PeersAdapter? = null

    private var torrent: TorrentData? = null
        set(value) {
            if (value != field) {
                field = value
                if (value != null) {
                    value.torrent.setPeersEnabled(true)
                    Rpc.gotTorrentPeersListener = gotTorrentPeersListener
                }
            }
        }

    private val gotTorrentPeersListener = { torrentId: Int ->
        if (torrentId == torrent?.id) {
            update()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = getActivity() as TorrentPropertiesActivity
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peersAdapter = PeersAdapter(activity)

        peers_view.adapter = peersAdapter
        peers_view.layoutManager = LinearLayoutManager(activity)
        peers_view.addItemDecoration(DividerItemDecoration(activity,
                DividerItemDecoration.VERTICAL))
        peers_view.itemAnimator = null
    }

    override fun onStart() {
        super.onStart()
        update()
        Rpc.gotTorrentPeersListener = gotTorrentPeersListener
    }

    override fun onStop() {
        super.onStop()
        Rpc.gotTorrentPeersListener = null
    }

    override fun onDestroyView() {
        peersAdapter = null
        super.onDestroyView()
    }

    fun update() {
        if (isAdded) {
            torrent = activity.torrent
            peersAdapter!!.update()

            if (torrent == null) {
                progress_bar!!.visibility = View.GONE
            } else {
                if (torrent!!.torrent.isPeersLoaded) {
                    progress_bar!!.visibility = View.GONE
                    placeholder!!.visibility = if (peersAdapter!!.itemCount == 0) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                } else {
                    progress_bar!!.visibility = View.VISIBLE
                }
            }
        }
    }
}