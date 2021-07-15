/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.databinding.ServerSettingsFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.collectWhenStarted


class ServerSettingsFragment : NavigationFragment(
    R.layout.server_settings_fragment,
    R.string.server_settings
) {
    private val binding by viewBinding(ServerSettingsFragmentBinding::bind)
    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

        GlobalRpc.status.collectWhenStarted(viewLifecycleOwner, ::updateView)
    }

    override fun onDestroyView() {
        snackbar = null
        super.onDestroyView()
    }

    private fun updateView(status: Rpc.Status) {
        snackbar?.dismiss()
        when (status.connectionState) {
            RpcConnectionState.Disconnected -> {
                snackbar = requireView().showSnackbar(
                    "",
                    Snackbar.LENGTH_INDEFINITE,
                    R.string.connect,
                    GlobalRpc.nativeInstance::connect
                ) {
                    snackbar = null
                }
                binding.placeholder.text = status.statusString
                hideKeyboard()
            }
            RpcConnectionState.Connecting -> {
                binding.placeholder.text = getString(R.string.connecting)
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
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            GlobalRpc.isConnected.collectWhenStarted(viewLifecycleOwner) {
                if (!it) {
                    navController.popBackStack()
                }
            }
        }
    }
}
