/*
 * Copyright (C) 2017-2020 Kevin Richter <me@kevinrichter.nl>, Alexey Rochev <equeim@gmail.com>
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
import org.equeim.tremotesf.rpc.Rpc
import org.equeim.tremotesf.rpc.Torrent
import org.equeim.tremotesf.ui.Settings
import org.equeim.tremotesf.ui.utils.AlphanumericComparator
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter


class DirectoriesViewAdapter(private val context: Context,
                             textView: AutoCompleteTextView) : AutoCompleteTextViewDynamicAdapter(textView) {
    private val directoriesMap = mutableMapOf<String, Int>()
    var directories = emptyList<String>()
        private set
    private val comparator = AlphanumericComparator()

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, Rpc.torrents.value.size)
        }
        val directory = directories[position - 1]
        val torrents = directoriesMap[directory]
        return context.getString(R.string.directories_spinner_text, directory, torrents)
    }

    override fun getCount(): Int {
        return directories.size + 1
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(directories.indexOf(Settings.torrentsDirectoryFilter) + 1)
    }

    fun getDirectoryFilter(position: Int): String {
        return if (position == 0) {
            ""
        } else {
            directories[position - 1]
        }
    }

    fun update(torrents: List<Torrent>) {
        directoriesMap.clear()
        for (torrent in torrents) {
            var directory = torrent.downloadDirectory
            if (directory.endsWith('/')) {
                directory = directory.dropLast(1)
            }
            directoriesMap[directory] = directoriesMap.getOrElse(directory) { 0 } + 1
        }
        directories = directoriesMap.keys.sortedWith(comparator)
        notifyDataSetChanged()
    }
}