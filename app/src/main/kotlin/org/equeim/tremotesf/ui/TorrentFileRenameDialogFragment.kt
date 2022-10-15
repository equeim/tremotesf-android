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
