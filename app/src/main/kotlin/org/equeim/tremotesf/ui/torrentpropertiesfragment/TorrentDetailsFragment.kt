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

import android.os.Bundle
import android.text.format.DateUtils
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentDetailsFragmentBinding
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.utils.*


class TorrentDetailsFragment :
    TorrentPropertiesFragment.PagerFragment(R.layout.torrent_details_fragment, TorrentPropertiesFragment.PagerAdapter.Tab.Details) {

    private var firstUpdate = true

    private val binding by viewBinding(TorrentDetailsFragmentBinding::bind)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        firstUpdate = true
        val propertiesFragmentModel = TorrentPropertiesFragmentViewModel.get(this)
        binding.hashTextView.text = propertiesFragmentModel.args.hash
        propertiesFragmentModel.torrent.launchAndCollectWhenStarted(viewLifecycleOwner, ::update)
    }

    private fun update(torrent: Torrent?) {
        if (torrent == null) return

        if (!torrent.isChanged && !firstUpdate) return

        firstUpdate = false

        with(binding) {
            completedTextView.text = FormatUtils.formatByteSize(requireContext(), torrent.completedSize)
            downloadedTextView.text = FormatUtils.formatByteSize(
                requireContext(),
                torrent.totalDownloaded
            )
            uploadedTextView.text = FormatUtils.formatByteSize(requireContext(), torrent.totalUploaded)

            ratioTextView.text = DecimalFormats.ratio.format(torrent.ratio)

            downloadSpeedTextView.text =
                FormatUtils.formatByteSpeed(requireContext(), torrent.downloadSpeed)
            uploadSpeedTextView.text = FormatUtils.formatByteSpeed(requireContext(), torrent.uploadSpeed)
            etaTextView.text = FormatUtils.formatDuration(requireContext(), torrent.eta)
            seedersTextView.text = torrent.seeders.toString()
            leechersTextView.text = torrent.leechers.toString()
            lastActivityTextView.text =
                DateUtils.getRelativeTimeSpanString(torrent.data.activityDateTime)

            totalSizeTextView.text = FormatUtils.formatByteSize(requireContext(), torrent.totalSize)

            val dir = torrent.downloadDirectory
            if (!dir.contentEquals(locationTextView.text)) {
                locationTextView.text = dir
            }

            creatorTextView.text = torrent.data.creator
            creationDateTextView.text =
                DateUtils.getRelativeTimeSpanString(torrent.data.creationDateTime)
            addedDateTextView.text = DateUtils.getRelativeTimeSpanString(torrent.addedDateTime)

            val comment: String = torrent.data.comment
            if (!comment.contentEquals(commentTextView.text)) {
                commentTextView.text = comment
            }
        }
    }
}
