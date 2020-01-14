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

import java.util.Comparator

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

import androidx.recyclerview.widget.RecyclerView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.peer_list_item.view.*


class PeersAdapter : ListAdapter<Peer, PeersAdapter.ViewHolder>(Callback()) {
    private val comparator = object : Comparator<Peer> {
        private val stringComparator = AlphanumericComparator()
        override fun compare(o1: Peer, o2: Peer) = stringComparator.compare(o1.address, o2.address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.peer_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun update(peers: List<Peer>) {
        submitList(peers.sortedWith(comparator))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val context = itemView.context

        private val addressTextView = itemView.address_text_view!!
        private val downloadSpeedTextView = itemView.download_speed_text_view!!
        private val uploadSpeedTextView = itemView.upload_speed_text_view!!
        private val progressTextView = itemView.progress_text_view!!
        private val clientTextView = itemView.client_text_view!!

        private var peerAddress = ""

        fun bind(peer: Peer) {
            if (peer.address != peerAddress) {
                peerAddress = peer.address
                addressTextView.text = peerAddress
                clientTextView.text = peer.client
            }
            downloadSpeedTextView.text = context.getString(R.string.download_speed_string, Utils.formatByteSpeed(context, peer.downloadSpeed))
            uploadSpeedTextView.text = context.getString(R.string.upload_speed_string, Utils.formatByteSpeed(context, peer.uploadSpeed))
            progressTextView.text = context.getString(R.string.progress_string, DecimalFormats.generic.format(peer.progress * 100))
        }
    }

    private class Callback : DiffUtil.ItemCallback<Peer>() {
        override fun areItemsTheSame(oldItem: Peer, newItem: Peer): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: Peer, newItem: Peer): Boolean {
            return oldItem.downloadSpeed == newItem.downloadSpeed &&
                    oldItem.uploadSpeed == newItem.uploadSpeed &&
                    oldItem.progress == newItem.progress
        }
    }
}