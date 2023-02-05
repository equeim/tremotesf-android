// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.navArgs
import kotlinx.parcelize.Parcelize

import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.createTextFieldDialog
import org.equeim.tremotesf.ui.utils.parcelable

class TorrentFileRenameDialogFragment : NavigationDialogFragment() {
    private val args: TorrentFileRenameDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createTextFieldDialog(
            requireContext(),
            null,
            getString(R.string.file_name),
            InputType.TYPE_TEXT_VARIATION_URI,
            args.fileName,
            null
        ) {
            val newName = it.textField.text?.toString() ?: ""
            setFragmentResult(
                RESULT_KEY,
                bundleOf(
                    FILE_RENAME_REQUEST_KEY to FileRenameRequest(
                        args.torrentId,
                        args.filePath,
                        newName
                    )
                )
            )
            activity?.actionMode?.finish()
        }
    }

    @Parcelize
    data class FileRenameRequest(
        val torrentId: Int,
        val filePath: String,
        val newName: String
    ) : Parcelable

    companion object {
        private val RESULT_KEY = TorrentFileRenameDialogFragment::class.qualifiedName!!
        private val FILE_RENAME_REQUEST_KEY = FileRenameRequest::class.qualifiedName!!

        fun setFragmentResultListenerForRpc(fragment: Fragment) =
            setFragmentResultListener(fragment) { (torrentId, filePath, newName) ->
                GlobalRpc.nativeInstance.renameTorrentFile(torrentId, filePath, newName)
            }

        fun setFragmentResultListener(fragment: Fragment, listener: (FileRenameRequest) -> Unit) {
            fragment.setFragmentResultListener(RESULT_KEY) { _, bundle ->
                bundle.parcelable<FileRenameRequest>(FILE_RENAME_REQUEST_KEY)?.let(listener)
            }
        }
    }
}
