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
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_seeding_fragment.*


class SeedingFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_seeding_fragment,
                                                            R.string.server_settings_seeding) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ratio_limit_check_box.isChecked = Rpc.serverSettings.ratioLimited
        ratio_limit_check_box.setOnCheckedChangeListener { _, checked ->
            ratio_limit_layout.isEnabled = checked
            Rpc.serverSettings.ratioLimited = checked
        }

        ratio_limit_layout.isEnabled = ratio_limit_check_box.isChecked
        val doubleFilter = DoubleFilter(0.0..10000.0)
        ratio_limit_edit.filters = arrayOf(doubleFilter)
        ratio_limit_edit.setText(DecimalFormats.ratio.format(Rpc.serverSettings.ratioLimit))
        ratio_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.ratioLimit = doubleFilter.parse(it.toString())!!
        }

        idle_seeding_check_box.isChecked = Rpc.serverSettings.idleSeedingLimited
        idle_seeding_check_box.setOnCheckedChangeListener { _, checked ->
            idle_seeding_limit_layout.isEnabled = checked
            Rpc.serverSettings.idleSeedingLimited = checked
        }

        idle_seeding_limit_layout.isEnabled = idle_seeding_check_box.isChecked

        idle_seeding_limit_edit.filters = arrayOf(IntFilter(0..10000))
        idle_seeding_limit_edit.setText(Rpc.serverSettings.idleSeedingLimit.toString())
        idle_seeding_limit_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.idleSeedingLimit = it.toString().toInt()
        }
    }
}