// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter


class TrackersViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private val trackersMap = mutableMapOf<String, Int>()
    val trackers = mutableListOf<String>()
    private val comparator = AlphanumericComparator()

    private var trackerFilter = ""

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, GlobalRpc.torrents.value.size)
        }
        val tracker = trackers[position - 1]
        val torrents = trackersMap[tracker]
        return context.getString(R.string.trackers_spinner_text, tracker, torrents)
    }

    override fun getCount(): Int {
        return trackers.size + 1
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(trackers.indexOf(trackerFilter) + 1)
    }

    fun getTrackerFilter(position: Int): String {
        return if (position == 0) {
            ""
        } else {
            trackers[position - 1]
        }
    }

    fun update(torrents: List<Torrent>, trackerFilter: String) {
        this.trackerFilter = trackerFilter

        trackersMap.clear()
        for (torrent in torrents) {
            for (tracker in torrent.trackerSites) {
                trackersMap[tracker] = trackersMap.getOrElse(tracker) { 0 } + 1
            }
        }
        trackers.clear()
        trackers.addAll(trackersMap.keys.sortedWith(comparator))
        notifyDataSetChanged()
    }
}