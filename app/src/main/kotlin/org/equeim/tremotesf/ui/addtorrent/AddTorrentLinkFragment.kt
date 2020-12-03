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

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.MenuItem
import android.view.View

import androidx.core.text.trimmedLength
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar

import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.AddTorrentLinkFragmentBinding
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.data.rpc.RpcConnectionState
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.textInputLayout
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.utils.collectWhenStarted


class AddTorrentLinkFragment : AddTorrentFragment(R.layout.add_torrent_link_fragment,
                                                  R.string.add_torrent_link,
                                                  R.menu.add_torrent_fragment_menu) {
    companion object {
        const val SCHEME_MAGNET = "magnet"
    }

    private val args: AddTorrentLinkFragmentArgs by navArgs()

    private val binding by viewBinding(AddTorrentLinkFragmentBinding::bind)

    private var doneMenuItem: MenuItem? = null
    private var snackbar: Snackbar? = null

    private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            torrentLinkEdit.setText(args.uri)

            priorityView.setText(R.string.normal_priority)
            priorityView.setAdapter(ArrayDropdownAdapter(priorityItems))

            startDownloadingCheckBox.isChecked = Rpc.serverSettings.startAddedTorrents
        }

        doneMenuItem = toolbar?.menu?.findItem(R.id.done)

        directoriesAdapter = AddTorrentFileFragment.setupDownloadDirectoryEdit(binding.downloadDirectoryLayout, this, savedInstanceState)

        Rpc.status.collectWhenStarted(viewLifecycleOwner, ::updateView)
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
            with(binding) {
                var error = false

                torrentLinkEdit.textInputLayout.error = if (torrentLinkEdit.text?.trimmedLength() == 0) {
                    error = true
                    getString(R.string.empty_field_error)
                } else {
                    null
                }

                val downloadDirectoryEdit = downloadDirectoryLayout.downloadDirectoryEdit
                val downloadDirectoryLayout = downloadDirectoryLayout.downloadDirectoryLayout

                downloadDirectoryLayout.error = if (downloadDirectoryEdit.text?.trimmedLength() == 0) {
                    error = true
                    getString(R.string.empty_field_error)
                } else {
                    null
                }

                if (error) {
                    return false
                }

                Rpc.nativeInstance.addTorrentLink(torrentLinkEdit.text?.toString() ?: "",
                                                  downloadDirectoryEdit.text.toString(),
                                                  priorityItemEnums[priorityItems.indexOf(priorityView.text.toString())],
                                                  startDownloadingCheckBox.isChecked)

                directoriesAdapter?.save()

                activity?.onBackPressed()
            }
            return true
        }

        return false
    }

    private fun updateView(status: Rpc.Status) {
        doneMenuItem?.isVisible = status.isConnected

        with(binding) {
            when (status.connectionState) {
                RpcConnectionState.Disconnected -> {
                    snackbar = requireView().showSnackbar("", Snackbar.LENGTH_INDEFINITE, R.string.connect) {
                        snackbar = null
                        Rpc.nativeInstance.connect()
                    }
                    placeholder.text = status.statusString

                    hideKeyboard()
                }
                RpcConnectionState.Connecting -> {
                    snackbar?.dismiss()
                    snackbar = null
                    placeholder.text = getString(R.string.connecting)
                }
            }

            if (status.isConnected) {
                if (scrollView.visibility != View.VISIBLE) {
                    downloadDirectoryLayout.downloadDirectoryEdit.setText(Rpc.serverSettings.downloadDirectory)
                    startDownloadingCheckBox.isChecked = Rpc.serverSettings.startAddedTorrents
                    scrollView.visibility = View.VISIBLE
                }
                placeholderLayout.visibility = View.GONE
            } else {
                placeholderLayout.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
            }

            progressBar.visibility = if (status.connectionState == RpcConnectionState.Connecting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
