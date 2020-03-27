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

package org.equeim.tremotesf.serversettingsfragment

import android.os.Bundle
import android.view.View

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_downloading_fragment.*


class DownloadingFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_downloading_fragment,
                                                                R.string.server_settings_downloading) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        download_directory_edit.setText(Rpc.serverSettings.downloadDirectory)
        download_directory_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.downloadDirectory = it.toString()
        }

        start_torrents_check_box.isChecked = Rpc.serverSettings.startAddedTorrents
        start_torrents_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.startAddedTorrents = checked
        }

        rename_incomplete_files_check_box.isChecked = Rpc.serverSettings.renameIncompleteFiles
        rename_incomplete_files_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.renameIncompleteFiles = checked
        }

        incomplete_files_directory_check_box.isChecked = Rpc.serverSettings.incompleteDirectoryEnabled
        incomplete_files_directory_check_box.setOnCheckedChangeListener { _, checked ->
            incomplete_files_directory_layout.isEnabled = checked
            Rpc.serverSettings.incompleteDirectoryEnabled = checked
        }

        incomplete_files_directory_layout.isEnabled = incomplete_files_directory_check_box.isChecked
        incomplete_files_directory_edit.setText(Rpc.serverSettings.incompleteDirectory)
        incomplete_files_directory_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.incompleteDirectory = it.toString()
        }
    }
}