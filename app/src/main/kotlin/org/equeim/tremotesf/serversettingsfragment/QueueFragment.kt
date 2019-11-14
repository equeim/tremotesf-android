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

package org.equeim.tremotesf.serversettingsfragment

import android.os.Bundle
import android.view.View

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_queue_fragment.*


class QueueFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_queue_fragment,
                                                          R.string.server_settings_queue) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        download_queue_check_box.isChecked = Rpc.serverSettings.isDownloadQueueEnabled
        download_queue_check_box.setOnCheckedChangeListener { _, checked ->
            download_queue_layout.isEnabled = checked
            Rpc.serverSettings.isDownloadQueueEnabled = checked
        }

        download_queue_layout.isEnabled = download_queue_check_box.isChecked
        download_queue_edit.filters = arrayOf(IntFilter(0..10000))
        download_queue_edit.setText(Rpc.serverSettings.downloadQueueSize().toString())
        download_queue_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setDownloadQueueSize(it.toString().toInt())
        }

        seed_queue_check_box.isChecked = Rpc.serverSettings.isSeedQueueEnabled
        seed_queue_check_box.setOnCheckedChangeListener { _, checked ->
            seed_queue_layout.isEnabled = checked
            Rpc.serverSettings.isSeedQueueEnabled = checked
        }

        seed_queue_layout.isEnabled = seed_queue_check_box.isChecked
        seed_queue_edit.filters = arrayOf(IntFilter(0..10000))
        seed_queue_edit.setText(Rpc.serverSettings.seedQueueSize().toString())
        seed_queue_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setSeedQueueSize(it.toString().toInt())
        }

        idle_queue_check_box.isChecked = Rpc.serverSettings.isIdleQueueLimited
        idle_queue_check_box.setOnCheckedChangeListener { _, checked ->
            idle_queue_layout.isEnabled = checked
            Rpc.serverSettings.isIdleQueueLimited = checked
        }

        idle_queue_layout.isEnabled = idle_queue_check_box.isChecked

        idle_queue_edit.filters = arrayOf(IntFilter(0..10000))
        idle_queue_edit.setText(Rpc.serverSettings.idleQueueLimit().toString())
        idle_queue_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setIdleQueueLimit(it.toString().toInt())
        }
    }
}