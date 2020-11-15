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
import android.view.View

import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.TorrentDetailsFragmentBinding
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.Utils
import org.equeim.tremotesf.ui.utils.viewBinding


class TorrentDetailsFragment : TorrentPropertiesFragment.PagerFragment(R.layout.torrent_details_fragment) {
    private var firstUpdate = true

    private val binding by viewBinding(TorrentDetailsFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.hashTextView.text = (requireParentFragment() as TorrentPropertiesFragment).hash
        firstUpdate = true
        update()
    }

    override fun update() {
        view ?: return

        val torrent = (requireParentFragment() as TorrentPropertiesFragment).torrent ?: return

        if (!torrent.isChanged && !firstUpdate) return

        firstUpdate = false

        with(binding) {
            completedTextView.text = Utils.formatByteSize(requireContext(), torrent.completedSize)
            downloadedTextView.text = Utils.formatByteSize(requireContext(),
                                                           torrent.totalDownloaded)
            uploadedTextView.text = Utils.formatByteSize(requireContext(), torrent.totalUploaded)

            ratioTextView.text = DecimalFormats.ratio.format(torrent.ratio)

            downloadSpeedTextView.text = Utils.formatByteSpeed(requireContext(), torrent.downloadSpeed)
            uploadSpeedTextView.text = Utils.formatByteSpeed(requireContext(), torrent.uploadSpeed)
            etaTextView.text = Utils.formatDuration(requireContext(), torrent.eta)
            seedersTextView.text = torrent.seeders.toString()
            leechersTextView.text = torrent.leechers.toString()
            lastActivityTextView.text = DateUtils.getRelativeTimeSpanString(torrent.data.activityDateTime)

            totalSizeTextView.text = Utils.formatByteSize(requireContext(), torrent.totalSize)

            val dir = torrent.downloadDirectory
            if (!dir.contentEquals(locationTextView.text)) {
                locationTextView.text = dir
            }

            creatorTextView.text = torrent.data.creator
            creationDateTextView.text = DateUtils.getRelativeTimeSpanString(torrent.data.creationDateTime)
            addedDateTextView.text = DateUtils.getRelativeTimeSpanString(torrent.addedDateTime)

            val comment: String = torrent.data.comment
            if (!comment.contentEquals(commentTextView.text)) {
                commentTextView.text = comment
            }
        }
    }
}
