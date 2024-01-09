// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.requests.Torrent
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter

class TrackersViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView,
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private data class TrackerItem(val tracker: String?, val torrents: Int)

    private var trackers = emptyList<TrackerItem>()
    private val comparator = object : Comparator<TrackerItem> {
        val trackerComparator = AlphanumericComparator()
        override fun compare(o1: TrackerItem, o2: TrackerItem): Int =
            trackerComparator.compare(o1.tracker, o2.tracker)
    }
    private var currentTrackerIndex = 0

    override fun getItem(position: Int): String {
        val tracker = trackers.getOrNull(position) ?: return ""
        return if (tracker.tracker != null) {
            context.getString(R.string.trackers_spinner_text, tracker.tracker, tracker.torrents)
        } else {
            context.getString(R.string.torrents_all, tracker.torrents)
        }
    }

    override fun getCount(): Int {
        return trackers.size
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(currentTrackerIndex)
    }

    fun getTrackerFilter(position: Int): String? {
        return trackers[position].tracker
    }

    fun update(torrents: List<Torrent>, trackerFilter: String) {
        trackers = torrents
            .asSequence()
            .flatMap { torrent -> torrent.trackerSites.asSequence().map { torrent to it } }
            .groupingBy { (_, tracker) -> tracker }
            .eachCount()
            .mapTo(mutableListOf(TrackerItem(null, torrents.size))) { (tracker, torrents) ->
                TrackerItem(
                    tracker,
                    torrents
                )
            }
            .apply { sortWith(comparator) }
        currentTrackerIndex = if (trackerFilter.isEmpty()) {
            0
        } else {
            trackers.indexOfFirst { it.tracker == trackerFilter }.takeUnless { it == -1 } ?: 0
        }
        notifyDataSetChanged()
    }
}
