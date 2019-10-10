/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc

import kotlinx.android.synthetic.main.server_settings_downloading_fragment.*


class DownloadingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        requireActivity().title = getString(R.string.server_settings_downloading)
        return inflater.inflate(R.layout.server_settings_downloading_fragment,
                                container,
                                false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        download_directory_edit.setText(Rpc.serverSettings.downloadDirectory())
        download_directory_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.setDownloadDirectory(s.toString())
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        start_torrents_check_box.isChecked = Rpc.serverSettings.startAddedTorrents()
        start_torrents_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.setStartAddedTorrents(checked)
        }

        rename_incomplete_files_check_box.isChecked = Rpc.serverSettings.renameIncompleteFiles()
        rename_incomplete_files_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.setRenameIncompleteFiles(checked)
        }

        incomplete_files_directory_check_box.isChecked = Rpc.serverSettings.isIncompleteDirectoryEnabled
        incomplete_files_directory_check_box.setOnCheckedChangeListener { _, checked ->
            incomplete_files_directory_edit.isEnabled = checked
            Rpc.serverSettings.isIncompleteDirectoryEnabled = checked
        }

        incomplete_files_directory_edit.isEnabled = incomplete_files_directory_check_box.isChecked
        incomplete_files_directory_edit.setText(Rpc.serverSettings.incompleteDirectory())

        incomplete_files_directory_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.setIncompleteDirectory(s.toString())
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