/*
 * Copyright (C) 2017-2022 Kevin Richter <me@kevinrichter.nl>, Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter
import org.equeim.tremotesf.ui.utils.toNativeSeparators


class DirectoriesViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private data class DirectoryItem(val path: String, val displayPath: String, val torrents: Int)

    private var directories = emptyList<DirectoryItem>()
    private val comparator = object : Comparator<DirectoryItem> {
        val pathComparator = AlphanumericComparator()
        override fun compare(o1: DirectoryItem?, o2: DirectoryItem?): Int =
            pathComparator.compare(o1?.displayPath, o2?.displayPath)
    }

    private var currentDirectory: DirectoryItem? = null

    override fun getItem(position: Int): String {
        if (position == 0) {
            return context.getString(R.string.torrents_all, GlobalRpc.torrents.value.size)
        }
        val directory = directories[position - 1]
        return context.getString(R.string.directories_spinner_text, directory.displayPath, directory.torrents)
    }

    override fun getCount(): Int {
        return directories.size + 1
    }

    override fun getCurrentItem(): CharSequence {
        val index = currentDirectory?.let {
            directories.indexOf(it).takeIf { it != -1 }
        } ?: 0
        return getItem(index)
    }

    fun getDirectoryPath(position: Int): String? {
        return if (position == 0) {
            null
        } else {
            directories[position - 1].path
        }
    }

    fun update(torrents: List<Torrent>, currentDirectoryPath: String) {
        directories = torrents.groupingBy { it.downloadDirectory }
            .eachCount()
            .map { (path, torrents) -> DirectoryItem(path, path.toNativeSeparators(), torrents) }
            .sortedWith(comparator)
        currentDirectory = directories.find { it.path == currentDirectoryPath }
        notifyDataSetChanged()
    }
}