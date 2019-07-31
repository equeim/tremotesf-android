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

import java.text.DecimalFormat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter

import kotlinx.android.synthetic.main.server_settings_seeding_fragment.*


class SeedingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        requireActivity().title = getString(R.string.server_settings_seeding)
        return inflater.inflate(R.layout.server_settings_seeding_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ratio_limit_check_box.isChecked = Rpc.instance.serverSettings.isRatioLimited
        ratio_limit_check_box.setOnCheckedChangeListener { _, checked ->
            ratio_limit_edit.isEnabled = checked
            Rpc.instance.serverSettings.isRatioLimited = checked
        }

        ratio_limit_edit.isEnabled = ratio_limit_check_box.isChecked
        ratio_limit_edit.filters = arrayOf(DoubleFilter(0.0..10000.0))
        ratio_limit_edit.setText(DecimalFormat("0.00").format(Rpc.instance.serverSettings.ratioLimit()))
        ratio_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.instance.serverSettings.setRatioLimit(DoubleFilter.parse(s.toString())!!)
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        idle_seeding_check_box.isChecked = Rpc.instance.serverSettings.isIdleSeedingLimited
        idle_seeding_check_box.setOnCheckedChangeListener { _, checked ->
            idle_seeding_layout.isEnabled = checked
            Rpc.instance.serverSettings.isIdleSeedingLimited = checked
        }

        idle_seeding_layout.isEnabled = idle_seeding_check_box.isChecked

        idle_seeding_edit.filters = arrayOf(IntFilter(0..10000))
        idle_seeding_edit.setText(Rpc.instance.serverSettings.idleSeedingLimit().toString())
        idle_seeding_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.instance.serverSettings.setIdleSeedingLimit(s.toString().toInt())
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