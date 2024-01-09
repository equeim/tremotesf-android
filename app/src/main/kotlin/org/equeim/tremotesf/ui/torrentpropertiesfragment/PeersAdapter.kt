// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.databinding.PeerListItemBinding
import org.equeim.tremotesf.rpc.requests.torrentproperties.Peer
import org.equeim.tremotesf.ui.utils.AsyncLoadingListAdapter
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.FormatUtils


class PeersAdapter : AsyncLoadingListAdapter<Peer, PeersAdapter.ViewHolder>(Callback()) {
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

    fun update(peers: List<Peer>?) {
        submitList(peers?.sortedWith(comparator))
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
                    FormatUtils.formatTransferRate(context, peer.downloadSpeed)
                )
                uploadSpeedTextView.text = context.getString(
                    R.string.upload_speed_string,
                    FormatUtils.formatTransferRate(context, peer.uploadSpeed)
                )
                progressTextView.text = context.getString(
                    R.string.progress_string,
                    DecimalFormats.generic.format(peer.progress * 100)
                )
            }
        }
    }

    private class Callback : DiffUtil.ItemCallback<Peer>() {
        override fun areItemsTheSame(oldItem: Peer, newItem: Peer) = oldItem.address == newItem.address
        override fun areContentsTheSame(oldItem: Peer, newItem: Peer) = oldItem == newItem
    }
}