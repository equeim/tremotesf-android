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

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View

import androidx.fragment.app.Fragment

import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.torrent_details_fragment.*


class TorrentDetailsFragment : Fragment(R.layout.torrent_details_fragment), TorrentPropertiesFragment.PagerFragment {
    private var firstUpdate = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hash_text_view.text = (requireParentFragment() as TorrentPropertiesFragment).hash
        firstUpdate = true
        update()
    }

    override fun update() {
        view ?: return

        val torrent = (requireParentFragment() as TorrentPropertiesFragment).torrent ?: return

        if (!torrent.isChanged && !firstUpdate) return

        firstUpdate = false

        completed_text_view.text = Utils.formatByteSize(requireContext(), torrent.completedSize)
        downloaded_text_view.text = Utils.formatByteSize(requireContext(),
                                                         torrent.totalDownloaded)
        uploaded_text_view.text = Utils.formatByteSize(requireContext(), torrent.totalUploaded)

        ratio_text_view.text = DecimalFormats.ratio.format(torrent.ratio)

        download_speed_text_view.text = Utils.formatByteSpeed(requireContext(), torrent.downloadSpeed)
        upload_speed_text_view.text = Utils.formatByteSpeed(requireContext(), torrent.uploadSpeed)
        eta_text_view.text = Utils.formatDuration(requireContext(), torrent.eta)
        seeders_text_view.text = torrent.seeders.toString()
        leechers_text_view.text = torrent.leechers.toString()
        last_activity_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.data.activityDateTime)

        total_size_text_view.text = Utils.formatByteSize(requireContext(), torrent.totalSize)

        val dir = torrent.downloadDirectory
        if (!dir.contentEquals(location_text_view.text)) {
            location_text_view.text = dir
        }

        creator_text_view.text = torrent.data.creator
        creation_date_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.data.creationDateTime)
        added_date_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.addedDateTime)

        val comment: String = torrent.data.comment
        if (!comment.contentEquals(comment_text_view.text)) {
            comment_text_view.text = comment
        }
    }
}