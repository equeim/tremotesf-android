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

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.PeerListItemBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils
import org.equeim.tremotesf.ui.utils.StateRestoringListAdapter
import java.util.Comparator


class PeersAdapter : StateRestoringListAdapter<Peer, PeersAdapter.ViewHolder>(Callback()) {
    private val comparator = object : Comparator<Peer> {
        private val stringComparator = AlphanumericComparator()
        override fun compare(o1: Peer, o2: Peer) = stringComparator.compare(o1.address, o2.address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            PeerListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.update(getItem(position))
    }

    fun update(peers: List<Peer>) {
        submitList(peers.sortedWith(comparator))
    }

    override fun allowStateRestoring(): Boolean {
        return GlobalRpc.isConnected.value
    }

    class ViewHolder(private val binding: PeerListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val context = itemView.context

        private var peerAddress = ""

        fun update(peer: Peer) {
            with(binding) {
                if (peer.address != peerAddress) {
                    peerAddress = peer.address
                    addressTextView.text = peerAddress
                    clientTextView.text = peer.client
                }
                downloadSpeedTextView.text = context.getString(
                    R.string.download_speed_string,
                    FormatUtils.formatByteSpeed(context, peer.downloadSpeed)
                )
                uploadSpeedTextView.text = context.getString(
                    R.string.upload_speed_string,
                    FormatUtils.formatByteSpeed(context, peer.uploadSpeed)
                )
                progressTextView.text = context.getString(
                    R.string.progress_string,
                    DecimalFormats.generic.format(peer.progress * 100)
                )
            }
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