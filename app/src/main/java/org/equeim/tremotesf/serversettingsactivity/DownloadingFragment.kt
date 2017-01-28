/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.serversettingsactivity

import android.app.Fragment
import android.os.Bundle

import android.text.Editable
import android.text.TextWatcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.CheckBox
import android.widget.EditText

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc


class DownloadingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_downloading)

        val view = inflater.inflate(R.layout.server_settings_downloading_fragment,
                                    container,
                                    false)

        val downloadDirectoryEdit = view.findViewById(R.id.download_directory_edit) as EditText
        downloadDirectoryEdit.setText(Rpc.serverSettings.downloadDirectory)
        downloadDirectoryEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.downloadDirectory = s.toString()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val startTorrentsCheckBox = view.findViewById(R.id.start_torrents_check_box) as CheckBox
        startTorrentsCheckBox.isChecked = Rpc.serverSettings.startAddedTorrents
        startTorrentsCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.startAddedTorrents = checked
        }

        val renameIncompleteFilesCheckBox = view.findViewById(R.id.rename_incomplete_files_check_box) as CheckBox
        renameIncompleteFilesCheckBox.isChecked = Rpc.serverSettings.renameIncompleteFiles
        renameIncompleteFilesCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.renameIncompleteFiles = checked
        }

        val incompleteFilesDirectoryCheckBox = view.findViewById(R.id.incomplete_files_directory_check_box) as CheckBox
        incompleteFilesDirectoryCheckBox.isChecked = Rpc.serverSettings.incompleteFilesDirectoryEnabled

        val incompleteFilesDirectoryEdit = view.findViewById(R.id.incomplete_files_directory_edit) as EditText
        incompleteFilesDirectoryEdit.isEnabled = incompleteFilesDirectoryCheckBox.isChecked
        incompleteFilesDirectoryEdit.setText(Rpc.serverSettings.incompleteFilesDirectory)

        incompleteFilesDirectoryCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            incompleteFilesDirectoryEdit.isEnabled = checked
            Rpc.serverSettings.incompleteFilesDirectoryEnabled = checked
        }

        incompleteFilesDirectoryEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.incompleteFilesDirectory = s.toString()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }
}