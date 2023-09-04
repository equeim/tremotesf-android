// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
// SPDX-FileCopyrightText: 2017 Kevin Richter <me@kevinrichter.nl>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.content.Context
import android.widget.AutoCompleteTextView
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.torrentfile.rpc.requests.Torrent
import org.equeim.tremotesf.torrentfile.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.utils.AutoCompleteTextViewDynamicAdapter

class DirectoriesViewAdapter(
    private val context: Context,
    textView: AutoCompleteTextView
) : AutoCompleteTextViewDynamicAdapter(textView) {
    private data class DirectoryItem(val path: String?, val displayPath: String?, val torrents: Int)

    private var directories = emptyList<DirectoryItem>()
    private val comparator = object : Comparator<DirectoryItem> {
        val pathComparator = AlphanumericComparator()
        override fun compare(o1: DirectoryItem, o2: DirectoryItem): Int =
            pathComparator.compare(o1.displayPath, o2.displayPath)
    }

    private var currentDirectoryIndex: Int = 0

    override fun getItem(position: Int): String {
        val directory = directories.getOrNull(position) ?: return ""
        return if (directory.displayPath != null) {
            context.getString(
                R.string.directories_spinner_text,
                directory.displayPath,
                directory.torrents
            )
        } else {
            context.getString(R.string.torrents_all, directory.torrents)
        }
    }

    override fun getCount(): Int {
        return directories.size
    }

    override fun getCurrentItem(): CharSequence {
        return getItem(currentDirectoryIndex)
    }

    fun getDirectoryPath(position: Int): String? {
        return directories[position].path
    }

    fun update(torrents: List<Torrent>, allTorrentsCount: Int, currentDirectoryPath: String) {
        directories = torrents.groupingBy { it.downloadDirectory }
            .eachCount()
            .mapTo(
                mutableListOf(DirectoryItem(null, null, allTorrentsCount))
            ) { (path, torrents) -> DirectoryItem(path.value, path.toNativeSeparators(), torrents) }
            .apply { sortWith(comparator) }
        currentDirectoryIndex = if (currentDirectoryPath.isEmpty()) {
            0
        } else {
            directories.indexOfFirst { it.path == currentDirectoryPath }.takeUnless { it == -1 } ?: 0
        }
        notifyDataSetChanged()
    }
}
