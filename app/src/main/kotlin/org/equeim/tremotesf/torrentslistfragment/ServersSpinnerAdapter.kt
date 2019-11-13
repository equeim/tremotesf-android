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

import android.widget.Spinner

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.utils.AlphanumericComparator
import org.equeim.tremotesf.utils.BaseSpinnerAdapter


class ServersSpinnerAdapter(private val serversSpinner: Spinner) : BaseSpinnerAdapter(R.string.server) {
    val servers = mutableListOf<Server>()

    private val comparator = object : Comparator<Server> {
        private val nameComparator = AlphanumericComparator()
        override fun compare(o1: Server, o2: Server) = nameComparator.compare(o1.name, o2.name)
    }

    init {
        serversSpinner.adapter = this
        update()
    }

    override fun getItem(position: Int): String {
        return servers[position].name
    }

    override fun getCount(): Int {
        return servers.size
    }

    fun update() {
        servers.clear()
        servers.addAll(Servers.servers.sortedWith(comparator))
        notifyDataSetChanged()
        serversSpinner.setSelection(servers.indexOf(Servers.currentServer.value))
    }
}