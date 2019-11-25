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

package org.equeim.tremotesf.torrentslistfragment

import java.util.Comparator

import android.widget.AutoCompleteTextView

import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.AutoCompleteTextViewDynamicAdapter


class ServersViewAdapter(textView: AutoCompleteTextView) : AutoCompleteTextViewDynamicAdapter(textView) {
    lateinit var servers: List<Server>
        private set

    private val comparator = object : Comparator<Server> {
        private val nameComparator = AlphanumericComparator()
        override fun compare(o1: Server, o2: Server) = nameComparator.compare(o1.name, o2.name)
    }

    override fun getItem(position: Int) = servers[position].name

    override fun getCount() = servers.size

    override fun getCurrentItem(): CharSequence {
        return Servers.currentServer.value?.name ?: ""
    }

    fun update() {
        servers = Servers.servers.value.sortedWith(comparator)
        notifyDataSetChanged()
    }
}