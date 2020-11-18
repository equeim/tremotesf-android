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

package org.equeim.tremotesf.ui.sidepanel

import android.content.Context
import android.widget.AutoCompleteTextView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.Torrent
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter


class TrackersViewAdapter(private val context: Context,
                          textView: AutoCompleteTextView) : AutoCompleteTextViewDynamicAdapter(textView) {
    private val trackersMap = mutableMapOf<String, Int>()
    val trackers = mutableListOf<String>()
    private val comparator = AlphanumericComparator()

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, Rpc.torrents.value.size)
        }
        val tracker = trackers[position - 1]
        val torrents = trackersMap[tracker]
        return context.getString(R.string.trackers_spinner_text, tracker, torrents)
    }

    override fun getCount(): Int {
        return trackers.size + 1
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(trackers.indexOf(Settings.torrentsTrackerFilter) + 1)
    }

    fun getTrackerFilter(position: Int): String {
        return if (position == 0) {
            ""
        } else {
            trackers[position - 1]
        }
    }

    fun update(torrents: List<Torrent>) {
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