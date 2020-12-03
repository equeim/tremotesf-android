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

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.navigation.fragment.navArgs

import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.utils.createTextFieldDialog

class TorrentFileRenameDialogFragment : NavigationDialogFragment() {
    private val args: TorrentFileRenameDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return createTextFieldDialog(requireContext(),
                                     null,
                                     getString(R.string.file_name),
                                     InputType.TYPE_TEXT_VARIATION_URI,
                                     args.fileName,
                                     null) {
            val newName = it.textField.text?.toString() ?: ""
            (parentFragmentManager.primaryNavigationFragment as PrimaryFragment?)?.onRenameFile(args.torrentId, args.filePath, newName)
            activity?.actionMode?.finish()
        }
    }

    interface PrimaryFragment {
        fun onRenameFile(torrentId: Int, filePath: String, newName: String)
    }
}