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

package org.equeim.tremotesf.ui.addtorrent

import android.os.Bundle
import android.view.View
import androidx.core.text.trimmedLength
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import org.equeim.libtremotesf.RpcConnectionState
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Rpc
import org.equeim.tremotesf.databinding.AddTorrentLinkFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.statusString
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.hideKeyboard
import org.equeim.tremotesf.ui.utils.showSnackbar
import org.equeim.tremotesf.ui.utils.textInputLayout
import org.equeim.tremotesf.ui.utils.viewBinding
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import timber.log.Timber


class AddTorrentLinkFragment : AddTorrentFragment(
    R.layout.add_torrent_link_fragment,
    R.string.add_torrent_link,
    0
) {
    companion object {
        val SCHEMES = arrayOf("magnet")
    }

    private val args: AddTorrentLinkFragmentArgs by navArgs()

    private val binding by viewBinding(AddTorrentLinkFragmentBinding::bind)

    private var snackbar: Snackbar? = null

    private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate: arguments = $arguments")
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            args.uri?.let { torrentLinkEdit.setText(it.toString()) }

            priorityView.setText(R.string.normal_priority)
            priorityView.setAdapter(ArrayDropdownAdapter(priorityItems))

            startDownloadingCheckBox.isChecked = GlobalRpc.serverSettings.startAddedTorrents

            addButton.setOnClickListener { addTorrentLink() }
        }

        directoriesAdapter = AddTorrentFileFragment.setupDownloadDirectoryEdit(
            binding.downloadDirectoryLayout,
            this,
            savedInstanceState
        )

        GlobalRpc.status.launchAndCollectWhenStarted(viewLifecycleOwner, ::updateView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directoriesAdapter?.saveInstanceState(outState)
    }

    override fun onDestroyView() {
        snackbar = null
        directoriesAdapter = null
        super.onDestroyView()
    }

    private fun addTorrentLink(): Unit = with(binding) {
        var error = false

        torrentLinkEdit.textInputLayout.error =
            if (torrentLinkEdit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        val downloadDirectoryEdit = downloadDirectoryLayout.downloadDirectoryEdit
        val downloadDirectoryLayout = downloadDirectoryLayout.downloadDirectoryLayout

        downloadDirectoryLayout.error =
            if (downloadDirectoryEdit.text?.trimmedLength() == 0) {
                error = true
                getString(R.string.empty_field_error)
            } else {
                null
            }

        if (error) {
            return
        }

        GlobalRpc.nativeInstance.addTorrentLink(
            torrentLinkEdit.text?.toString() ?: "",
            downloadDirectoryEdit.text.toString(),
            priorityItemEnums[priorityItems.indexOf(priorityView.text.toString())].swigValue(),
            startDownloadingCheckBox.isChecked
        )

        directoriesAdapter?.save()

        requiredActivity.onBackPressedDispatcher.onBackPressed()
    }

    private fun updateView(status: Rpc.Status) {
        with(binding) {
            when (status.connectionState) {
                RpcConnectionState.Disconnected -> {
                    snackbar = requireView().showSnackbar(
                        "",
                        Snackbar.LENGTH_INDEFINITE,
                        R.string.connect
                    ) {
                        snackbar = null
                        GlobalRpc.nativeInstance.connect()
                    }
                    placeholder.text = status.statusString

                    hideKeyboard()
                }
                RpcConnectionState.Connecting -> {
                    snackbar?.dismiss()
                    snackbar = null
                    placeholder.text = getString(R.string.connecting)
                }
                RpcConnectionState.Connected -> {}
            }

            if (status.isConnected) {
                if (scrollView.visibility != View.VISIBLE) {
                    downloadDirectoryLayout.downloadDirectoryEdit.setText(GlobalRpc.serverSettings.downloadDirectory)
                    startDownloadingCheckBox.isChecked = GlobalRpc.serverSettings.startAddedTorrents
                    scrollView.visibility = View.VISIBLE
                }
                placeholderLayout.visibility = View.GONE
                addButton.show()
            } else {
                placeholderLayout.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
                addButton.hide()
            }

            progressBar.visibility = if (status.connectionState == RpcConnectionState.Connecting) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}
