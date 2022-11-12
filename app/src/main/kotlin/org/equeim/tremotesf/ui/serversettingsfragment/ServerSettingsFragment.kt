/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.*


class ServerSettingsFragment : NavigationFragment(
    R.layout.server_settings_fragment,
    R.string.server_settings
) {
    private val binding by viewLifecycleObject(ServerSettingsFragmentBinding::bind)
    private var connectSnackbar: Snackbar? by viewLifecycleObjectNullable()

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        binding.listView.apply {
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

        GlobalRpc.status.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateView)
    }

    private fun updateView(status: Rpc.Status) {
        when (status.connectionState) {
            RpcConnectionState.Disconnected -> {
                connectSnackbar = binding.root.showSnackbar(
                    message = "",
                    length = Snackbar.LENGTH_INDEFINITE,
                    actionText = R.string.connect,
                    action = GlobalRpc.nativeInstance::connect
                ) {
                    if (connectSnackbar == it) {
                        connectSnackbar = null
                    }
                }
                binding.placeholder.text = status.statusString
                hideKeyboard()
            }
            RpcConnectionState.Connecting -> {
                binding.placeholder.text = getString(R.string.connecting)
                connectSnackbar?.dismiss()
                connectSnackbar = null
            }
            RpcConnectionState.Connected -> {
                connectSnackbar?.dismiss()
                connectSnackbar = null
            }
        }

        with(binding) {
            if (status.connectionState == RpcConnectionState.Connected) {
                listView.visibility = View.VISIBLE
                placeholderLayout.visibility = View.GONE
            } else {
                placeholderLayout.visibility = View.VISIBLE
                listView.visibility = View.GONE
            }

            progressBar.visibility = if (status.connectionState == RpcConnectionState.Connecting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    open class BaseFragment(
        @LayoutRes contentLayoutId: Int,
        @StringRes titleRes: Int
    ) : NavigationFragment(contentLayoutId, titleRes) {
        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            GlobalRpc.isConnected.launchAndCollectWhenStarted(viewLifecycleOwner) {
                if (!it) {
                    navController.popBackStack()
                }
            }
        }
    }
}
