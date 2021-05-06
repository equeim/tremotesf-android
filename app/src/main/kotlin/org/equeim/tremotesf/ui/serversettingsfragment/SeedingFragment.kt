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
import org.equeim.tremotesf.databinding.ServerSettingsSeedingFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.DoubleFilter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewBinding

class SeedingFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_seeding_fragment,
    R.string.server_settings_seeding
) {
    private val binding by viewBinding(ServerSettingsSeedingFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            ratioLimitCheckBox.isChecked = GlobalRpc.serverSettings.ratioLimited
            ratioLimitCheckBox.setDependentViews(ratioLimitLayout) { checked ->
                GlobalRpc.serverSettings.ratioLimited = checked
            }

            val doubleFilter = DoubleFilter(0.0..10000.0)
            ratioLimitEdit.filters = arrayOf(doubleFilter)
            ratioLimitEdit.setText(DecimalFormats.ratio.format(GlobalRpc.serverSettings.ratioLimit))
            ratioLimitEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.ratioLimit = doubleFilter.parse(it.toString())!!
            }

            idleSeedingCheckBox.isChecked = GlobalRpc.serverSettings.idleSeedingLimited
            idleSeedingCheckBox.setDependentViews(idleSeedingLimitLayout) { checked ->
                GlobalRpc.serverSettings.idleSeedingLimited = checked
            }

            idleSeedingLimitEdit.filters = arrayOf(IntFilter(0..10000))
            idleSeedingLimitEdit.setText(GlobalRpc.serverSettings.idleSeedingLimit.toString())
            idleSeedingLimitEdit.doAfterTextChangedAndNotEmpty {
                GlobalRpc.serverSettings.idleSeedingLimit = it.toString().toInt()
            }
        }
    }
}