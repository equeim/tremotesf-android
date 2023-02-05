// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.navigation.fragment.navArgs
import org.equeim.libtremotesf.IntVector
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.SetLocationDialogBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.addtorrent.AddTorrentDirectoriesAdapter
import org.equeim.tremotesf.ui.utils.createTextFieldDialog
import org.equeim.tremotesf.ui.utils.normalizePath
import org.equeim.tremotesf.ui.utils.toNativeSeparators

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
            defaultText = args.location.toNativeSeparators(),
            onInflatedView = {
                it.downloadDirectoryLayout.downloadDirectoryEdit.let { edit ->
                    directoriesAdapter = AddTorrentDirectoriesAdapter(edit, savedInstanceState)
                    edit.setAdapter(directoriesAdapter)
                }
            },
            onAccepted = {
                GlobalRpc.nativeInstance.setTorrentsLocation(
                    IntVector(args.torrentIds),
                    it.downloadDirectoryLayout.downloadDirectoryEdit.text.toString().normalizePath(),
                    it.moveFilesCheckBox.isChecked
                )
                directoriesAdapter?.save()
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        directoriesAdapter?.saveInstanceState(outState)
    }
}