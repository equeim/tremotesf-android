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

import android.os.Bundle
import android.view.View

import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.navigation.fragment.findNavController

import org.equeim.libtremotesf.Torrent

abstract class AddTorrentFragment(@LayoutRes contentLayoutId: Int,
                                  @StringRes titleRes: Int,
                                  @MenuRes toolbarMenuRes: Int) : NavigationFragment(contentLayoutId, titleRes, toolbarMenuRes) {
    companion object {
        const val URI = "uri"
    }

    protected lateinit var priorityItems: Array<String>
    protected val priorityItemEnums = arrayOf(Torrent.Priority.HighPriority,
                                              Torrent.Priority.NormalPriority,
                                              Torrent.Priority.LowPriority)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItems = resources.getStringArray(R.array.priority_items)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.setNavigationOnClickListener {
            if (requireActivity().isTaskRoot) {
                findNavController().navigateUp()
            } else {
                // For some reason it is needed to finish activity before navigateUp(),
                // otherwise we won't switch to our task in some cases
                requireActivity().finish()
                findNavController().navigateUp()
            }
        }
    }
}
