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
import org.equeim.tremotesf.databinding.ServerSettingsQueueFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewBinding


class QueueFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_queue_fragment,
    R.string.server_settings_queue
) {
    private val binding by viewBinding(ServerSettingsQueueFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            downloadQueueCheckBox.isChecked = GlobalRpc.serverSettings.downloadQueueEnabled
            downloadQueueCheckBox.setDependentViews(downloadQueueLayout) { checked ->
                GlobalRpc.serverSettings.downloadQueueEnabled = checked
            }

            downloadQueueEdit.filters = arrayOf(IntFilter(0..10000))
            downloadQueueEdit.setText(GlobalRpc.serverSettings.downloadQueueSize.toString())
            downloadQueueEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.downloadQueueSize = it.toString().toInt()
            }

            seedQueueCheckBox.isChecked = GlobalRpc.serverSettings.seedQueueEnabled
            seedQueueCheckBox.setDependentViews(seedQueueLayout) { checked ->
                GlobalRpc.serverSettings.seedQueueEnabled = checked
            }

            seedQueueEdit.filters = arrayOf(IntFilter(0..10000))
            seedQueueEdit.setText(GlobalRpc.serverSettings.seedQueueSize.toString())
            seedQueueEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.seedQueueSize = it.toString().toInt()
            }

            idleQueueCheckBox.isChecked = GlobalRpc.serverSettings.idleQueueLimited
            idleQueueCheckBox.setDependentViews(idleQueueLayout) { checked ->
                GlobalRpc.serverSettings.idleQueueLimited = checked
            }

            idleQueueEdit.filters = arrayOf(IntFilter(0..10000))
            idleQueueEdit.setText(GlobalRpc.serverSettings.idleQueueLimit.toString())
            idleQueueEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.idleQueueLimit = it.toString().toInt()
            }
        }
    }
}