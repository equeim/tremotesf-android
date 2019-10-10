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

package org.equeim.tremotesf.mainactivity

import android.content.Context

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.BaseSpinnerAdapter


class TrackersSpinnerAdapter(private val context: Context) : BaseSpinnerAdapter(R.string.trackers) {
    private val trackersMap = mutableMapOf<String, Int>()
    val trackers = mutableListOf<String>()
    private val comparator = AlphanumericComparator()

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, Rpc.torrents.size)
        }
        val tracker = trackers[position - 1]
        val torrents = trackersMap[tracker]
        return context.getString(R.string.trackers_spinner_text, tracker, torrents)
    }

    override fun getCount(): Int {
        return trackers.size + 1
    }

    fun update() {
        trackersMap.clear()
        for (torrent in Rpc.torrents) {
            for (tracker in torrent.trackers) {
                trackersMap[tracker] = trackersMap.getOrElse(tracker) { 0 } + 1
            }
        }
        trackers.clear()
        trackers.addAll(trackersMap.keys.sortedWith(comparator))
        notifyDataSetChanged()
    }
}