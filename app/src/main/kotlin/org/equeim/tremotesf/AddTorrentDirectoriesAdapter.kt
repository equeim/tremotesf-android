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

package org.equeim.tremotesf

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText

import androidx.appcompat.widget.AppCompatAutoCompleteTextView

import org.equeim.tremotesf.utils.AlphanumericComparator


class AddTorrentDirectoriesAdapter(private val textEdit: EditText,
                                   savedInstanceState: Bundle?) : ArrayAdapter<String>(textEdit.context,
                                                                                       R.layout.download_directory_dropdown_item,
                                                                                       android.R.id.text1,
                                                                                       retrieveItems(savedInstanceState)) {
    companion object {
        private const val STATE_KEY = "org.equeim.tremotesf.AddTorrentDirectoriesAdapter.items"

        private fun retrieveItems(savedInstanceState: Bundle?): ArrayList<String> {
            savedInstanceState?.getStringArrayList(STATE_KEY)?.let { return it }

            val comparator = AlphanumericComparator()
            val items = Servers.currentServer.value?.addTorrentDialogDirectories?.toSortedSet(comparator) ?: sortedSetOf(comparator)
            Rpc.torrents.value?.let {
                for (torrent in it) {
                    items.add(torrent.downloadDirectory)
                }
            }
            val downloadDirectory = Rpc.serverSettings.downloadDirectory()
            items.add(downloadDirectory)
            return ArrayList(items)
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        if (view.tag == null) {
            view.tag = ViewHolder(view)
        }
        val holder = view.tag as ViewHolder
        holder.position = position
        return view
    }

    private fun getItems(): ArrayList<String> {
        val items = ArrayList<String>(count)
        for (i in 0 until count) {
            items.add(getItem(i)!!)
        }
        return items
    }

    fun save() {
        val items = getItems()
        val trimmed = textEdit.text.trim().toString()
        if (!items.contains(trimmed)) {
            items.add(trimmed)
        }
        Servers.currentServer.value?.addTorrentDialogDirectories = items.toTypedArray()
        Servers.save()
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_KEY, getItems())
    }

    private inner class ViewHolder(view: View) {
        var position = -1
        init {
            view.findViewById<View>(R.id.remove_button).setOnClickListener {
                if (count > 1) {
                    remove(getItem(position))
                }
            }
        }
    }
}

class NonFilteringAutoCompleteTextView(context: Context,
                                       attrs: AttributeSet?,
                                       defStyleAttr: Int) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {
    constructor(context: Context,
                attrs: AttributeSet?) : this(context, attrs, R.attr.autoCompleteTextViewStyle)
    constructor(context: Context) : this(context, null)

    override fun performFiltering(text: CharSequence?, keyCode: Int) {
        // Do nothing
        onFilterComplete(adapter.count)
    }
}