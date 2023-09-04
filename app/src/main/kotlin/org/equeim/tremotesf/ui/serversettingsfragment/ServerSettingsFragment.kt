// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import android.widget.ArrayAdapter
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsFragmentBinding
import org.equeim.tremotesf.ui.NavigationFragment

class ServerSettingsFragment : NavigationFragment(
    R.layout.server_settings_fragment,
    R.string.server_settings
) {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        ServerSettingsFragmentBinding.bind(requireView()).listView.apply {
            adapter = ArrayAdapter(
                requireContext(),
                R.layout.server_settings_fragment_list_item,
                resources.getStringArray(R.array.server_settings_items)
            )
            divider = null
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ ->
                val directions = when (position) {
                    0 -> ServerSettingsFragmentDirections.toDownloadingFragment()
                    1 -> ServerSettingsFragmentDirections.toSeedingFragment()
                    2 -> ServerSettingsFragmentDirections.toQueueFragment()
                    3 -> ServerSettingsFragmentDirections.toSpeedFragment()
                    4 -> ServerSettingsFragmentDirections.toNetworkFragment()
                    else -> null
                }
                if (directions != null) {
                    navigate(directions)
                }
            }
        }
    }
}
