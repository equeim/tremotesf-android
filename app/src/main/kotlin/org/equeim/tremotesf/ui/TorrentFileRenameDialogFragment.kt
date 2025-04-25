// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import androidx.navigation.fragment.navArgs
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
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
                        args.torrentHashString,
                        args.filePath,
                        newName
                    )
                )
            )
            activity?.actionMode?.value?.finish()
        }
    }

    @Parcelize
    data class FileRenameRequest(
        val torrentHashString: String?,
        val filePath: String,
        val newName: String,
    ) : Parcelable

    companion object {
        private val RESULT_KEY = TorrentFileRenameDialogFragment::class.qualifiedName!!
        private val FILE_RENAME_REQUEST_KEY = FileRenameRequest::class.qualifiedName!!

        fun setFragmentResultListener(fragment: Fragment, listener: (FileRenameRequest) -> Unit) {
            fragment.parentFragmentManager.setFragmentResultListener(
                RESULT_KEY,
                fragment.viewLifecycleOwner
            ) { _, bundle ->
                bundle.parcelable<FileRenameRequest>(FILE_RENAME_REQUEST_KEY)?.let(listener)
            }
        }
    }
}
