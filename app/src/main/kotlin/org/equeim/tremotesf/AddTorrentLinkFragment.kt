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

package org.equeim.tremotesf

import android.os.Bundle
import android.view.MenuItem
import android.view.View

import androidx.core.text.trimmedLength
import com.google.android.material.snackbar.Snackbar

import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.hideKeyboard
import org.equeim.tremotesf.utils.showSnackbar
import org.equeim.tremotesf.utils.textInputLayout

import kotlinx.android.synthetic.main.add_torrent_link_fragment.*
import kotlinx.android.synthetic.main.download_directory_edit.*


class AddTorrentLinkFragment : AddTorrentFragment(R.layout.add_torrent_link_fragment,
                                                  R.string.add_torrent_link,
                                                  R.menu.add_torrent_activity_menu) {
    companion object {
        const val SCHEME_MAGNET = "magnet"
    }

    private var doneMenuItem: MenuItem? = null
    private var snackbar: Snackbar? = null

    private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        torrent_link_edit.setText(requireArguments().getString(URI))

        priority_view.setText(R.string.normal_priority)
        priority_view.setAdapter(ArrayDropdownAdapter(priorityItems))

        start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents

        doneMenuItem = toolbar?.menu?.findItem(R.id.done)

        directoriesAdapter = AddTorrentFileFragment.setupDownloadDirectoryEdit(this, savedInstanceState)

        Rpc.status.observe(viewLifecycleOwner) { updateView() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directoriesAdapter?.saveInstanceState(outState)
    }

    override fun onDestroyView() {
        doneMenuItem = null
        snackbar = null
        directoriesAdapter = null
        super.onDestroyView()
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.done) {
            var error = false

            torrent_link_edit.textInputLayout.error = if (torrent_link_edit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

            download_directory_layout.error = if (download_directory_edit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

            if (error) {
                return false
            }

            Rpc.nativeInstance.addTorrentLink(torrent_link_edit.text?.toString() ?: "",
                                              download_directory_edit.text.toString(),
                                              priorityItemEnums[priorityItems.indexOf(priority_view.text.toString())],
                                              start_downloading_check_box.isChecked)

            directoriesAdapter?.save()

            activity?.onBackPressed()

            return true
        }

        return false
    }

    private fun updateView() {
        doneMenuItem?.isVisible = Rpc.isConnected

        when (Rpc.status.value) {
            RpcStatus.Disconnected -> {
                snackbar = requireView().showSnackbar("", Snackbar.LENGTH_INDEFINITE, R.string.connect) {
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
            if (scroll_view.visibility != View.VISIBLE) {
                download_directory_edit.setText(Rpc.serverSettings.downloadDirectory)
                start_downloading_check_box.isChecked = Rpc.serverSettings.startAddedTorrents
                scroll_view.visibility = View.VISIBLE
            }
            placeholder_layout.visibility = View.GONE
        } else {
            placeholder_layout.visibility = View.VISIBLE
            scroll_view.visibility = View.GONE
        }

        progress_bar.visibility = if (Rpc.status.value == RpcStatus.Connecting) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}