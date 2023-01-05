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

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsDownloadingFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.normalizePath
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.toNativeSeparators


class DownloadingFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_downloading_fragment,
    R.string.server_settings_downloading
) {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(ServerSettingsDownloadingFragmentBinding.bind(requireView())) {
            downloadDirectoryEdit.setText(GlobalRpc.serverSettings.downloadDirectory.toNativeSeparators())
            downloadDirectoryEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.downloadDirectory = it.toString().normalizePath()
            }

            startTorrentsCheckBox.isChecked = GlobalRpc.serverSettings.startAddedTorrents
            startTorrentsCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.startAddedTorrents = checked
            }

            renameIncompleteFilesCheckBox.isChecked = GlobalRpc.serverSettings.renameIncompleteFiles
            renameIncompleteFilesCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.renameIncompleteFiles = checked
            }

            incompleteFilesDirectoryCheckBox.isChecked =
                GlobalRpc.serverSettings.incompleteDirectoryEnabled
            incompleteFilesDirectoryCheckBox.setDependentViews(incompleteFilesDirectoryLayout) { checked ->
                GlobalRpc.serverSettings.incompleteDirectoryEnabled = checked
            }

            incompleteFilesDirectoryEdit.setText(GlobalRpc.serverSettings.incompleteDirectory.toNativeSeparators())
            incompleteFilesDirectoryEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.incompleteDirectory = it.toString().normalizePath()
            }
        }
    }
}