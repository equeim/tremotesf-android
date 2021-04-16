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

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import android.view.View

import org.equeim.tremotesf.R
import org.equeim.tremotesf.data.rpc.Rpc
import org.equeim.tremotesf.databinding.ServerSettingsDownloadingFragmentBinding
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewBinding


class DownloadingFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_downloading_fragment,
                                                                R.string.server_settings_downloading) {
    private val binding by viewBinding(ServerSettingsDownloadingFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with (binding) {
            downloadDirectoryEdit.setText(Rpc.serverSettings.downloadDirectory)
            downloadDirectoryEdit.doAfterTextChangedAndNotEmpty {
                Rpc.serverSettings.downloadDirectory = it.toString()
            }

            startTorrentsCheckBox.isChecked = Rpc.serverSettings.startAddedTorrents
            startTorrentsCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.startAddedTorrents = checked
            }

            renameIncompleteFilesCheckBox.isChecked = Rpc.serverSettings.renameIncompleteFiles
            renameIncompleteFilesCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.renameIncompleteFiles = checked
            }

            incompleteFilesDirectoryCheckBox.isChecked = Rpc.serverSettings.incompleteDirectoryEnabled
            incompleteFilesDirectoryCheckBox.setDependentViews(incompleteFilesDirectoryLayout) { checked ->
                Rpc.serverSettings.incompleteDirectoryEnabled = checked
            }

            incompleteFilesDirectoryEdit.setText(Rpc.serverSettings.incompleteDirectory)
            incompleteFilesDirectoryEdit.doAfterTextChangedAndNotEmpty {
                Rpc.serverSettings.incompleteDirectory = it.toString()
            }
        }
    }
}