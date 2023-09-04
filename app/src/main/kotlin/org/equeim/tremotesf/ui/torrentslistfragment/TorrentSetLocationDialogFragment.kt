// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.SetLocationDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.torrentfile.rpc.requests.torrentproperties.setTorrentsLocation
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentDirectoriesAdapter
import org.equeim.tremotesf.ui.utils.createTextFieldDialog

class TorrentSetLocationDialogFragment : NavigationDialogFragment() {
    private val args: TorrentSetLocationDialogFragmentArgs by navArgs()
    private var directoriesAdapter: AddTorrentDirectoriesAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createTextFieldDialog(requireContext(),
            title = null,
            viewBindingFactory = SetLocationDialogBinding::inflate,
            textFieldId = R.id.download_directory_edit,
            textFieldLayoutId = R.id.download_directory_layout,
            hint = getString(R.string.location),
            inputType = InputType.TYPE_TEXT_VARIATION_URI,
            defaultText = args.location,
            onInflatedView = {
                directoriesAdapter = AddTorrentDirectoriesAdapter(
                    lifecycleScope,
                    savedInstanceState
                )
                it.downloadDirectoryLayout.downloadDirectoryEdit.setAdapter(directoriesAdapter)
            },
            onAccepted = {
                GlobalRpcClient.performBackgroundRpcRequest(R.string.torrent_set_location_error) {
                    GlobalRpcClient.setTorrentsLocation(
                        args.torrentHashStrings.asList(),
                        it.downloadDirectoryLayout.downloadDirectoryEdit.text.toString(),
                        it.moveFilesCheckBox.isChecked
                    )
                }
                directoriesAdapter?.save(it.downloadDirectoryLayout.downloadDirectoryEdit)
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directoriesAdapter?.saveInstanceState(outState)
    }
}
