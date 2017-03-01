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

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.IntFilter

import kotlinx.android.synthetic.main.server_settings_queue_fragment.*


class QueueFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_queue)
        return inflater.inflate(R.layout.server_settings_queue_fragment, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        download_queue_check_box.isChecked = Rpc.serverSettings.downloadQueueEnabled
        download_queue_check_box.setOnCheckedChangeListener { _, checked ->
            download_queue_edit.isEnabled = checked
            Rpc.serverSettings.downloadQueueEnabled = checked
        }

        download_queue_edit.isEnabled = download_queue_check_box.isChecked
        download_queue_edit.filters = arrayOf(IntFilter(0..10000))
        download_queue_edit.setText(Rpc.serverSettings.downloadQueueSize.toString())
        download_queue_edit.addTextChangedListener(object : TextWatcher {
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

        seed_queue_check_box.isChecked = Rpc.serverSettings.seedQueueEnabled
        seed_queue_check_box.setOnCheckedChangeListener { _, checked ->
            seed_queue_edit.isEnabled = checked
            Rpc.serverSettings.seedQueueEnabled = checked
        }

        seed_queue_edit.isEnabled = seed_queue_check_box.isChecked
        seed_queue_edit.filters = arrayOf(IntFilter(0..10000))
        seed_queue_edit.setText(Rpc.serverSettings.seedQueueSize.toString())
        seed_queue_edit.addTextChangedListener(object : TextWatcher {
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

        idle_queue_check_box.isChecked = Rpc.serverSettings.idleQueueLimited
        idle_queue_check_box.setOnCheckedChangeListener { _, checked ->
            idle_queue_layout.isEnabled = checked
            Rpc.serverSettings.idleQueueLimited = checked
        }

        idle_queue_layout.isEnabled = idle_queue_check_box.isChecked

        idle_queue_edit.filters = arrayOf(IntFilter(0..10000))
        idle_queue_edit.setText(Rpc.serverSettings.idleQueueLimit.toString())
        idle_queue_edit.addTextChangedListener(object : TextWatcher {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }
}