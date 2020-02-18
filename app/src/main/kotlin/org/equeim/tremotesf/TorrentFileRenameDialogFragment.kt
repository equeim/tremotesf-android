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

import android.app.Dialog
import android.os.Bundle
import android.text.InputType

import org.equeim.tremotesf.utils.createTextFieldDialog

import kotlinx.android.synthetic.main.text_field_dialog.*

class TorrentFileRenameDialogFragment : NavigationDialogFragment() {
    companion object {
        const val TORRENT_ID = "torrentId"
        const val FILE_PATH = "filePath"
        const val FILE_NAME = "fileName"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fileName = requireArguments().getString(FILE_NAME)
        return createTextFieldDialog(requireContext(),
                                     null,
                                     null,
                                     null,
                                     getString(R.string.file_name),
                                     InputType.TYPE_TEXT_VARIATION_URI,
                                     fileName,
                                     null) {
            val torrentId = requireArguments().getInt(TORRENT_ID)
            val path = requireArguments().getString(FILE_PATH)!!
            val newName = requireDialog().text_field.text.toString()
            (parentFragmentManager.primaryNavigationFragment as PrimaryFragment?)?.onRenameFile(torrentId, path, newName)
            activity?.actionMode?.finish()
        }
    }

    interface PrimaryFragment {
        fun onRenameFile(torrentId: Int, filePath: String, newName: String)
    }
}