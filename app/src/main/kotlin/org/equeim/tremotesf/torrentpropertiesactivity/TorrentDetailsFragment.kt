/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import java.text.DecimalFormat

import android.os.Bundle
import android.text.format.DateUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.support.v4.app.Fragment

import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.Utils

import kotlinx.android.synthetic.main.torrent_details_fragment.*


class TorrentDetailsFragment : Fragment() {
    private var firstUpdate = true

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.torrent_details_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hash_text_view.text = (activity as TorrentPropertiesActivity).hash
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firstUpdate = true
    }

    override fun onStart() {
        super.onStart()
        update()
    }

    fun update() {
        val activity = this.activity
        if (activity != null && view != null) {
            val torrent = (activity as TorrentPropertiesActivity).torrent
            torrent ?: return

            if (!torrent.isChanged && !firstUpdate) return

            firstUpdate = false

            completed_text_view.text = Utils.formatByteSize(activity, torrent.completedSize())
            downloaded_text_view.text = Utils.formatByteSize(activity,
                                                             torrent.totalDownloaded())
            uploaded_text_view.text = Utils.formatByteSize(activity, torrent.totalUploaded())

            ratio_text_view.text = DecimalFormat("0.00").format(torrent.ratio())

            download_speed_text_view.text = Utils.formatByteSpeed(activity, torrent.downloadSpeed())
            upload_speed_text_view.text = Utils.formatByteSpeed(activity, torrent.uploadSpeed())
            eta_text_view.text = Utils.formatDuration(activity, torrent.eta())
            seeders_text_view.text = torrent.seeders().toString()
            leechers_text_view.text = torrent.leechers().toString()
            last_activity_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.activityDate().time)

            total_size_text_view.text = Utils.formatByteSize(activity, torrent.totalSize())

            val dir = torrent.downloadDirectory()
            if (dir != location_text_view!!.text.toString()) {
                location_text_view!!.text = dir
            }

            creator_text_view.text = torrent.creator()
            creation_date_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.creationDate().time)
            added_date_text_view.text = DateUtils.getRelativeTimeSpanString(torrent.addedDate().time)

            val comment: String = torrent.comment()
            if (comment != comment_text_view.text.toString()) {
                comment_text_view.text = comment
            }
        }
    }
}