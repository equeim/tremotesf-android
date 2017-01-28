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
import org.equeim.tremotesf.utils.IntFilter


class QueueFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_queue)

        val view = inflater.inflate(R.layout.server_settings_queue_fragment, container, false)

        val downloadQueueCheckBox = view.findViewById(R.id.download_queue_check_box) as CheckBox
        downloadQueueCheckBox.isChecked = Rpc.serverSettings.downloadQueueEnabled

        val downloadQueueEdit = view.findViewById(R.id.download_queue_edit) as EditText
        downloadQueueEdit.isEnabled = downloadQueueCheckBox.isChecked
        downloadQueueEdit.filters = arrayOf(IntFilter(0..10000))
        downloadQueueEdit.setText(Rpc.serverSettings.downloadQueueSize.toString())

        downloadQueueCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            downloadQueueEdit.isEnabled = checked
            Rpc.serverSettings.downloadQueueEnabled = checked
        }

        downloadQueueEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.downloadQueueSize = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val seedQueueCheckBox = view.findViewById(R.id.seed_queue_check_box) as CheckBox
        seedQueueCheckBox.isChecked = Rpc.serverSettings.seedQueueEnabled

        val seedQueueEdit = view.findViewById(R.id.seed_queue_edit) as EditText
        seedQueueEdit.isEnabled = seedQueueCheckBox.isChecked
        seedQueueEdit.filters = arrayOf(IntFilter(0..10000))
        seedQueueEdit.setText(Rpc.serverSettings.seedQueueSize.toString())

        seedQueueCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            seedQueueEdit.isEnabled = checked
            Rpc.serverSettings.seedQueueEnabled = checked
        }

        seedQueueEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.seedQueueSize = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val idleQueueCheckbox = view.findViewById(R.id.idle_queue_check_box) as CheckBox
        idleQueueCheckbox.isChecked = Rpc.serverSettings.idleQueueLimited

        val idleQueueLayout = view.findViewById(R.id.idle_queue_layout)
        idleQueueLayout.isEnabled = idleQueueCheckbox.isChecked

        idleQueueCheckbox.setOnCheckedChangeListener { checkBox, checked ->
            idleQueueLayout.isEnabled = checked
            Rpc.serverSettings.idleQueueLimited = checked
        }

        val idleQueueEdit = view.findViewById(R.id.idle_queue_edit) as EditText
        idleQueueEdit.filters = arrayOf(IntFilter(0..10000))
        idleQueueEdit.setText(Rpc.serverSettings.idleQueueLimit.toString())

        idleQueueEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.idleQueueLimit = s.toString().toInt()
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