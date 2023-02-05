// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsQueueFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import timber.log.Timber


class QueueFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_queue_fragment,
    R.string.server_settings_queue
) {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(ServerSettingsQueueFragmentBinding.bind(requireView())) {
            downloadQueueCheckBox.isChecked = GlobalRpc.serverSettings.downloadQueueEnabled
            downloadQueueCheckBox.setDependentViews(downloadQueueLayout) { checked ->
                GlobalRpc.serverSettings.downloadQueueEnabled = checked
            }

            downloadQueueEdit.filters = arrayOf(IntFilter(0..10000))
            downloadQueueEdit.setText(GlobalRpc.serverSettings.downloadQueueSize.toString())
            downloadQueueEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.downloadQueueSize = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse download queue size $it")
                }
            }

            seedQueueCheckBox.isChecked = GlobalRpc.serverSettings.seedQueueEnabled
            seedQueueCheckBox.setDependentViews(seedQueueLayout) { checked ->
                GlobalRpc.serverSettings.seedQueueEnabled = checked
            }

            seedQueueEdit.filters = arrayOf(IntFilter(0..10000))
            seedQueueEdit.setText(GlobalRpc.serverSettings.seedQueueSize.toString())
            seedQueueEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.seedQueueSize = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse seed queue size $it")
                }
            }

            idleQueueCheckBox.isChecked = GlobalRpc.serverSettings.idleQueueLimited
            idleQueueCheckBox.setDependentViews(idleQueueLayout) { checked ->
                GlobalRpc.serverSettings.idleQueueLimited = checked
            }

            idleQueueEdit.filters = arrayOf(IntFilter(0..10000))
            idleQueueEdit.setText(GlobalRpc.serverSettings.idleQueueLimit.toString())
            idleQueueEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.idleQueueLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse idle queue limit $it")
                }
            }
        }
    }
}