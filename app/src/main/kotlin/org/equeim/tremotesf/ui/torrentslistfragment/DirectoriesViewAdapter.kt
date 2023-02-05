// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
// SPDX-FileCopyrightText: 2017 Kevin Richter <me@kevinrichter.nl>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
