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

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText

import androidx.appcompat.widget.ListPopupWindow

import org.equeim.tremotesf.utils.AlphanumericComparator

class AddTorrentDirectoriesAdapter(private val textEdit: EditText) : ArrayAdapter<String>(textEdit.context, R.layout.download_directory_dropdown_item, android.R.id.text1) {
    companion object {
        fun setupPopup(dropDownButton: View, textEdit: EditText): AddTorrentDirectoriesAdapter {
            val adapter = AddTorrentDirectoriesAdapter(textEdit)

            val popup = ListPopupWindow(dropDownButton.context)
            popup.setAdapter(adapter)
            popup.anchorView = dropDownButton
            popup.isModal = true
            dropDownButton.setOnClickListener {
                popup.show()
            }
            dropDownButton.setOnTouchListener(popup.createDragToOpenListener(dropDownButton))
            popup.setOnItemClickListener { _, _, position, _ ->
                textEdit.setText(adapter.getItem(position))
                popup.dismiss()
            }

            dropDownButton.viewTreeObserver.addOnGlobalLayoutListener {
                popup.setContentWidth((dropDownButton.parent as View).width)
            }

            return adapter
        }
    }

    init {
        val comparator = AlphanumericComparator()
        val items = Servers.currentServer?.addTorrentDialogDirectories?.toSortedSet(comparator) ?: sortedSetOf(comparator)
        for (torrent in Rpc.instance.torrents) {
            items.add(torrent.downloadDirectory)
        }
        val downloadDirectory = Rpc.instance.serverSettings.downloadDirectory()
        items.add(downloadDirectory)
        addAll(items)
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

    fun save() {
        val items = mutableListOf<String>()
        for (i in 0 until count) {
            items.add(getItem(i)!!)
        }
        if (!items.contains(textEdit.text.trim())) {
            items.add(textEdit.text.toString())
        }
        Servers.currentServer?.addTorrentDialogDirectories = items.toTypedArray()
        Servers.save()
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