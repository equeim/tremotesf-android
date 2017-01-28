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

import java.text.DecimalFormat

import android.app.Fragment
import android.os.Bundle
import android.text.format.DateUtils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.Utils


class TorrentDetailsFragment : Fragment() {
    private var completedTextView: TextView? = null
    private var downloadedTextView: TextView? = null
    private var uploadedTextView: TextView? = null

    private var ratioTextView: TextView? = null
    private var downloadSpeedTextView: TextView? = null
    private var uploadSpeedTextView: TextView? = null
    private var etaTextView: TextView? = null
    private var seedersTextView: TextView? = null
    private var leechersTextView: TextView? = null
    private var lastActivityTextView: TextView? = null

    private var totalSizeTextView: TextView? = null
    private var locationTextView: TextView? = null
    private var hashTextView: TextView? = null
    private var creatorTextView: TextView? = null
    private var creationDateTextView: TextView? = null
    private var commentTextView: TextView? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.torrent_details_fragment, container, false)

        completedTextView = view.findViewById(R.id.completed_text_view) as TextView
        downloadedTextView = view.findViewById(R.id.downloaded_text_view) as TextView
        uploadedTextView = view.findViewById(R.id.uploaded_text_view) as TextView
        ratioTextView = view.findViewById(R.id.ratio_text_view) as TextView
        downloadSpeedTextView = view.findViewById(R.id.download_speed_text_view) as TextView
        uploadSpeedTextView = view.findViewById(R.id.upload_speed_text_view) as TextView
        etaTextView = view.findViewById(R.id.eta_text_view) as TextView
        seedersTextView = view.findViewById(R.id.seeders_text_view) as TextView
        leechersTextView = view.findViewById(R.id.leechers_text_view) as TextView
        lastActivityTextView = view.findViewById(R.id.last_activity_text_view) as TextView

        totalSizeTextView = view.findViewById(R.id.total_size_text_view) as TextView
        locationTextView = view.findViewById(R.id.location_text_view) as TextView
        hashTextView = view.findViewById(R.id.hash_text_view) as TextView
        creatorTextView = view.findViewById(R.id.creator_text_view) as TextView
        creationDateTextView = view.findViewById(R.id.creation_date_text_view) as TextView
        commentTextView = view.findViewById(R.id.comment_text_view) as TextView

        hashTextView!!.text = (activity as TorrentPropertiesActivity).hash

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        completedTextView = null
        downloadedTextView = null
        uploadedTextView = null
        ratioTextView = null
        downloadSpeedTextView = null
        uploadSpeedTextView = null
        etaTextView = null
        seedersTextView = null
        leechersTextView = null
        lastActivityTextView = null
        totalSizeTextView = null
        locationTextView = null
        hashTextView = null
        creatorTextView = null
        creationDateTextView = null
        commentTextView = null
    }

    override fun onStart() {
        super.onStart()
        update()
    }

    fun update() {
        if (view != null) {
            val torrent = (activity as TorrentPropertiesActivity).torrent
            if (torrent == null) {
                return
            }

            completedTextView!!.text = Utils.formatByteSize(activity, torrent.completedSize)
            downloadedTextView!!.text = Utils.formatByteSize(activity,
                                                             torrent.totalDownloaded)
            uploadedTextView!!.text = Utils.formatByteSize(activity, torrent.totalUploaded)

            ratioTextView!!.text = DecimalFormat("0.00").format(torrent.ratio)

            downloadSpeedTextView!!.text = Utils.formatByteSpeed(activity, torrent.downloadSpeed)
            uploadSpeedTextView!!.text = Utils.formatByteSpeed(activity, torrent.uploadSpeed)
            etaTextView!!.text = Utils.formatDuration(activity, torrent.eta)
            seedersTextView!!.text = torrent.seeders.toString()
            leechersTextView!!.text = torrent.leechers.toString()
            lastActivityTextView!!.text = DateUtils.getRelativeTimeSpanString(torrent.activityDate.time)

            totalSizeTextView!!.text = Utils.formatByteSize(activity, torrent.totalSize)

            if (torrent.downloadDirectory != locationTextView!!.text.toString()) {
                locationTextView!!.text = torrent.downloadDirectory
            }

            creatorTextView!!.text = torrent.creator
            creationDateTextView!!.text = DateUtils.getRelativeTimeSpanString(torrent.creationDate.time)

            if (torrent.comment != commentTextView!!.text.toString()) {
                commentTextView!!.text = torrent.comment
            }
        }
    }
}