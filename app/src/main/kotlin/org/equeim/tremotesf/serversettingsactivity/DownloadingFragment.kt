/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc

import kotlinx.android.synthetic.main.server_settings_downloading_fragment.*


class DownloadingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_downloading)
        return inflater.inflate(R.layout.server_settings_downloading_fragment,
                                container,
                                false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        download_directory_edit.setText(Rpc.serverSettings.downloadDirectory)
        download_directory_edit.addTextChangedListener(object : TextWatcher {
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

        start_torrents_check_box.isChecked = Rpc.serverSettings.startAddedTorrents
        start_torrents_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.startAddedTorrents = checked
        }

        rename_incomplete_files_check_box.isChecked = Rpc.serverSettings.renameIncompleteFiles
        rename_incomplete_files_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.renameIncompleteFiles = checked
        }

        incomplete_files_directory_check_box.isChecked = Rpc.serverSettings.incompleteFilesDirectoryEnabled
        incomplete_files_directory_check_box.setOnCheckedChangeListener { _, checked ->
            incomplete_files_directory_edit.isEnabled = checked
            Rpc.serverSettings.incompleteFilesDirectoryEnabled = checked
        }

        incomplete_files_directory_edit.isEnabled = incomplete_files_directory_check_box.isChecked
        incomplete_files_directory_edit.setText(Rpc.serverSettings.incompleteFilesDirectory)

        incomplete_files_directory_edit.addTextChangedListener(object : TextWatcher {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }
}