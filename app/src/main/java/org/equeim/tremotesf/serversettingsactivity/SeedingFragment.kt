/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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

import android.app.Fragment
import android.os.Bundle

import android.text.Editable
import android.text.TextWatcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.CheckBox
import android.widget.EditText

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter


class SeedingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_seeding)

        val view = inflater.inflate(R.layout.server_settings_seeding_fragment, container, false)

        val ratioLimitCheckBox = view.findViewById(R.id.ratio_limit_check_box) as CheckBox
        ratioLimitCheckBox.isChecked = Rpc.serverSettings.ratioLimited

        val ratioLimitEdit = view.findViewById(R.id.ratio_limit_edit) as EditText
        ratioLimitEdit.isEnabled = ratioLimitCheckBox.isChecked
        ratioLimitEdit.filters = arrayOf(DoubleFilter(0..10000))
        ratioLimitEdit.setText(DecimalFormat("0.00").format(Rpc.serverSettings.ratioLimit))

        ratioLimitCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            ratioLimitEdit.isEnabled = checked
            Rpc.serverSettings.ratioLimited = checked
        }

        ratioLimitEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.ratioLimit = DoubleFilter.parse(s.toString())!!
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val idleSeedingCheckBox = view.findViewById(R.id.idle_seeding_check_box) as CheckBox
        idleSeedingCheckBox.isChecked = Rpc.serverSettings.idleSeedingLimited

        val idleSeedingLayout = view.findViewById(R.id.idle_seeding_layout)
        idleSeedingLayout.isEnabled = idleSeedingCheckBox.isChecked

        idleSeedingCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            idleSeedingLayout.isEnabled = checked
            Rpc.serverSettings.idleSeedingLimited = checked
        }

        val idleSeedingEdit = view.findViewById(R.id.idle_seeding_edit) as EditText
        idleSeedingEdit.filters = arrayOf(IntFilter(0..10000))
        idleSeedingEdit.setText(Rpc.serverSettings.idleSeedingLimit.toString())

        idleSeedingEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.idleSeedingLimit = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }
}