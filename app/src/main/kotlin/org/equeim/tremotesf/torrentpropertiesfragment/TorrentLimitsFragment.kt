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

package org.equeim.tremotesf.torrentpropertiesfragment

import android.os.Bundle
import android.view.View

import androidx.fragment.app.Fragment

import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.DecimalFormats
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.torrent_limits_fragment.*
import org.equeim.tremotesf.Torrent


class TorrentLimitsFragment : Fragment(R.layout.torrent_limits_fragment) {
    private companion object {
        const val MAX_SPEED_LIMIT = 4 * 1024 * 1024 // kilobytes per second
        const val MAX_RATIO_LIMIT = 10000.0
        const val MAX_IDLE_SEEDING_LIMIT = 10000 // minutes
        const val MAX_PEERS = 10000

        // Should match R.array.priority_items
        val priorityItems = arrayOf(TorrentData.Priority.HighPriority,
                                    TorrentData.Priority.NormalPriority,
                                    TorrentData.Priority.LowPriority)

        // Should match R.array.ratio_limit_mode
        val ratioLimitModeItems = arrayOf(TorrentData.RatioLimitMode.GlobalRatioLimit,
                                          TorrentData.RatioLimitMode.UnlimitedRatio,
                                          TorrentData.RatioLimitMode.SingleRatioLimit)

        // Should match R.array.idle_seeding_mode
        val idleSeedingModeItems = arrayOf(TorrentData.IdleSeedingLimitMode.GlobalIdleSeedingLimit,
                                           TorrentData.IdleSeedingLimitMode.UnlimitedIdleSeeding,
                                           TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit)
    }

    private var torrent: Torrent? = null

    private lateinit var priorityItemValues: Array<String>
    private lateinit var ratioLimitModeItemValues: Array<String>
    private lateinit var idleSeedingModeItemValues: Array<String>

    private lateinit var doubleFilter: DoubleFilter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItemValues = resources.getStringArray(R.array.priority_items)
        ratioLimitModeItemValues = resources.getStringArray(R.array.ratio_limit_mode)
        idleSeedingModeItemValues = resources.getStringArray(R.array.idle_seeding_mode)
        doubleFilter = DoubleFilter(0.0..MAX_RATIO_LIMIT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scroll_view.isEnabled = false

        download_speed_limit_edit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

        upload_speed_limit_edit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

        priority_view.setAdapter(ArrayDropdownAdapter(priorityItemValues))

        ratio_limit_mode_view.setAdapter(ArrayDropdownAdapter(ratioLimitModeItemValues))
        ratio_limit_edit.filters = arrayOf(doubleFilter)

        idle_seeding_mode_view.setAdapter(ArrayDropdownAdapter(idleSeedingModeItemValues))
        idle_seeding_limit_edit.filters = arrayOf(IntFilter(0..MAX_IDLE_SEEDING_LIMIT))

        maximum_peers_edit.filters = arrayOf(IntFilter(0..MAX_PEERS))
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        update(restored = priority_view.text.isNotEmpty())

        global_limits_check_box.setOnCheckedChangeListener { _, checked ->
            torrent?.setHonorSessionLimits(checked)
        }

        download_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            download_speed_limit_layout.isEnabled = checked
            torrent?.setDownloadSpeedLimited(checked)
        }
        download_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            torrent?.setDownloadSpeedLimit(it.toString().toInt())
        }

        upload_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            upload_speed_limit_layout.isEnabled = checked
            torrent?.setUploadSpeedLimited(checked)
        }
        upload_speed_limit_edit.doAfterTextChangedAndNotEmpty {
            torrent?.setUploadSpeedLimit(it.toString().toInt())
        }

        priority_view.setOnItemClickListener { _, _, position, _ ->
            torrent?.setBandwidthPriority(priorityItems[position])
        }

        ratio_limit_mode_view.setOnItemClickListener { _, _, position, _ ->
            val mode = ratioLimitModeItems[position]
            ratio_limit_layout.isEnabled = (mode == TorrentData.RatioLimitMode.SingleRatioLimit)
            torrent?.setRatioLimitMode(mode)
        }
        ratio_limit_edit.doAfterTextChangedAndNotEmpty {
            torrent?.setRatioLimit(doubleFilter.parse(it.toString())!!)
        }

        idle_seeding_mode_view.setOnItemClickListener { _, _, position, _ ->
            val mode = idleSeedingModeItems[position]
            idle_seeding_limit_layout.isEnabled = (mode == TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit)
            torrent?.setIdleSeedingLimitMode(mode)
        }
        idle_seeding_limit_edit.doAfterTextChangedAndNotEmpty {
            torrent?.setIdleSeedingLimit(it.toString().toInt())
        }

        maximum_peers_edit.doAfterTextChangedAndNotEmpty {
            torrent?.setPeersLimit(it.toString().toInt())
        }
    }

    private fun update(restored: Boolean = false) {
        val torrent = (requireParentFragment() as TorrentPropertiesFragment).torrent ?: return
        this.torrent = torrent

        val data = torrent.data

        if (!restored) {
            global_limits_check_box.isChecked = data.honorSessionLimits

            download_speed_limit_check_box.isChecked = data.downloadSpeedLimited
            download_speed_limit_edit.setText(data.downloadSpeedLimit.toString())

            upload_speed_limit_check_box.isChecked = data.uploadSpeedLimited
            upload_speed_limit_edit.setText(data.uploadSpeedLimit.toString())

            priority_view.setText(priorityItemValues[priorityItems.indexOf(data.bandwidthPriority)])

            ratio_limit_mode_view.setText(ratioLimitModeItemValues[ratioLimitModeItems.indexOf(data.ratioLimitMode)])
            ratio_limit_edit.setText(DecimalFormats.ratio.format(data.ratioLimit))

            idle_seeding_mode_view.setText(idleSeedingModeItemValues[idleSeedingModeItems.indexOf(data.idleSeedingLimitMode)])
            idle_seeding_limit_edit.setText(data.idleSeedingLimit.toString())

            maximum_peers_edit.setText(data.peersLimit.toString())
        }

        download_speed_limit_layout.isEnabled = download_speed_limit_check_box.isChecked
        upload_speed_limit_layout.isEnabled = upload_speed_limit_check_box.isChecked
        ratio_limit_layout.isEnabled = (ratio_limit_mode_view.text.toString() == ratioLimitModeItemValues[ratioLimitModeItems.indexOf(TorrentData.RatioLimitMode.SingleRatioLimit)])
        idle_seeding_limit_layout.isEnabled = (idle_seeding_mode_view.text.toString() == idleSeedingModeItemValues[idleSeedingModeItems.indexOf(TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit)])
    }
}
