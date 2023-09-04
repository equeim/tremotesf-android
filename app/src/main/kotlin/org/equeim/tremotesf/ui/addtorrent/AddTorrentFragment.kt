// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.TorrentLimits
import org.equeim.tremotesf.ui.NavigationFragment

abstract class AddTorrentFragment(
    @LayoutRes contentLayoutId: Int,
    @StringRes titleRes: Int,
    @MenuRes toolbarMenuRes: Int
) : NavigationFragment(contentLayoutId, titleRes, toolbarMenuRes) {
    protected lateinit var priorityItems: Array<String>
    protected val priorityItemEnums = arrayOf(
        TorrentLimits.BandwidthPriority.High,
        TorrentLimits.BandwidthPriority.Normal,
        TorrentLimits.BandwidthPriority.Low
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItems = resources.getStringArray(R.array.priority_items)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        toolbar.setNavigationOnClickListener {
            if (!requireActivity().isTaskRoot) {
                // FIXME: https://issuetracker.google.com/issues/145231159
                // For some reason it is needed to finish activity before navigateUp(),
                // otherwise we won't switch to our task in some cases
                requireActivity().finish()
            }
            navController.navigateUp()
        }
    }
}
