/*
 * Copyright (C) 2017-2018 Kevin Richter <me@kevinrichter.nl>, Alexey Rochev <equeim@gmail.com>
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

import java.text.Collator

import android.content.Context

import com.amjjd.alphanum.AlphanumericComparator

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.BaseSpinnerAdapter


class DirectoriesSpinnerAdapter(private val context: Context) : BaseSpinnerAdapter(R.string.directories) {
    private val directoriesMap = mutableMapOf<String, Int>()
    val directories = mutableListOf<String>()
    private val comparator = AlphanumericComparator(Collator.getInstance())

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, Rpc.instance.torrents.size)
        }
        val directory = directories[position - 1]
        val torrents = directoriesMap[directory]
        return context.getString(R.string.directories_spinner_text, directory, torrents)
    }

    override fun getCount(): Int {
        return directories.size + 1
    }

    fun update() {
        directoriesMap.clear()
        for (torrent in Rpc.instance.torrents) {
            directoriesMap[torrent.downloadDirectory] = directoriesMap.getOrElse(torrent.downloadDirectory) { 0 } + 1
        }
        directories.clear()
        directories.addAll(directoriesMap.keys.sortedWith(comparator))
        notifyDataSetChanged()
    }
}