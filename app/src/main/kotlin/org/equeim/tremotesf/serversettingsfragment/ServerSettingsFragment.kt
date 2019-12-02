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

package org.equeim.tremotesf.serversettingsfragment

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes

import com.google.android.material.snackbar.Snackbar

import org.jetbrains.anko.design.indefiniteSnackbar

import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.RpcStatus
import org.equeim.tremotesf.utils.hideKeyboard

import kotlinx.android.synthetic.main.server_settings_fragment.*


class ServerSettingsFragment : NavigationFragment(R.layout.server_settings_fragment,
                                                  R.string.server_settings) {
    private var snackbar: Snackbar? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        list_view.adapter = ArrayAdapter(requireContext(),
                                         R.layout.server_settings_fragment_list_item,
                                         resources.getStringArray(R.array.server_settings_items))
        list_view.divider = null
        list_view.setSelector(android.R.color.transparent)
        list_view.setOnItemClickListener { _, _, position, _ ->
            navController.navigate(when (position) {
                                             0 -> R.id.action_serverSettingsFragment_to_downloadingFragment
                                             1 -> R.id.action_serverSettingsFragment_to_seedingFragment
                                             2 -> R.id.action_serverSettingsFragment_to_queueFragment
                                             3 -> R.id.action_serverSettingsFragment_to_speedFragment
                                             4 -> R.id.action_serverSettingsFragment_to_networkFragment
                                             else -> 0 })
        }
        Rpc.status.observe(viewLifecycleOwner) { updateView() }
    }

    override fun onDestroyView() {
        snackbar = null
        super.onDestroyView()
    }

    private fun updateView() {
        when (Rpc.status.value) {
            RpcStatus.Disconnected -> {
                snackbar = view?.indefiniteSnackbar("", getString(R.string.connect)) {
                    snackbar = null
                    Rpc.nativeInstance.connect()
                }
                placeholder.text = Rpc.statusString

                hideKeyboard()
            }
            RpcStatus.Connecting -> {
                snackbar?.dismiss()
                snackbar = null
                placeholder.text = getString(R.string.connecting)
            }
        }

        if (Rpc.isConnected) {
            list_view.visibility = View.VISIBLE
            placeholder_layout.visibility = View.GONE
        } else {
            placeholder_layout.visibility = View.VISIBLE
            list_view.visibility = View.GONE
        }

        progress_bar.visibility = if (Rpc.status.value == RpcStatus.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    open class BaseFragment(@LayoutRes contentLayoutId: Int,
                            @StringRes titleRes: Int) : NavigationFragment(contentLayoutId, titleRes) {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            Rpc.status.observe(viewLifecycleOwner, ::onRpcStatusChanged)
        }

        private fun onRpcStatusChanged(status: Int) {
            if (status == RpcStatus.Disconnected) {
                navController.popBackStack()
            }
        }
    }
}
