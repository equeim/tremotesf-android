// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsSeedingFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.DoubleFilter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import timber.log.Timber

class SeedingFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_seeding_fragment,
    R.string.server_settings_seeding
) {
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(ServerSettingsSeedingFragmentBinding.bind(requireView())) {
            ratioLimitCheckBox.isChecked = GlobalRpc.serverSettings.ratioLimited
            ratioLimitCheckBox.setDependentViews(ratioLimitLayout) { checked ->
                GlobalRpc.serverSettings.ratioLimited = checked
            }

            val doubleFilter = DoubleFilter(0.0..10000.0)
            ratioLimitEdit.filters = arrayOf(doubleFilter)
            ratioLimitEdit.setText(DecimalFormats.ratio.format(GlobalRpc.serverSettings.ratioLimit))
            ratioLimitEdit.doAfterTextChangedAndNotEmpty {
                val limit = doubleFilter.parseOrNull(it.toString())
                if (limit != null) {
                    GlobalRpc.serverSettings.ratioLimit = limit
                } else {
                    Timber.e("Failed to parse ratio limit $it")
                }
            }

            idleSeedingCheckBox.isChecked = GlobalRpc.serverSettings.idleSeedingLimited
            idleSeedingCheckBox.setDependentViews(idleSeedingLimitLayout) { checked ->
                GlobalRpc.serverSettings.idleSeedingLimited = checked
            }

            idleSeedingLimitEdit.filters = arrayOf(IntFilter(0..10000))
            idleSeedingLimitEdit.setText(GlobalRpc.serverSettings.idleSeedingLimit.toString())
            idleSeedingLimitEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.idleSeedingLimit = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse idle seeding limit $it")
                }
            }
        }
    }
}