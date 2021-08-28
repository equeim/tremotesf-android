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

package org.equeim.tremotesf.ui.torrentpropertiesfragment

import android.os.Bundle
import org.equeim.libtremotesf.TorrentData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Torrent
import org.equeim.tremotesf.databinding.TorrentLimitsFragmentBinding
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.DecimalFormats
import org.equeim.tremotesf.ui.utils.DoubleFilter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.viewBinding


class TorrentLimitsFragment :
    TorrentPropertiesFragment.PagerFragment(R.layout.torrent_limits_fragment) {
    private companion object {
        const val MAX_SPEED_LIMIT = 4 * 1024 * 1024 // kilobytes per second
        const val MAX_RATIO_LIMIT = 10000.0
        const val MAX_IDLE_SEEDING_LIMIT = 10000 // minutes
        const val MAX_PEERS = 10000

        // Should match R.array.priority_items
        val priorityItems = arrayOf(
            TorrentData.Priority.HighPriority,
            TorrentData.Priority.NormalPriority,
            TorrentData.Priority.LowPriority
        )

        // Should match R.array.ratio_limit_mode
        val ratioLimitModeItems = arrayOf(
            TorrentData.RatioLimitMode.GlobalRatioLimit,
            TorrentData.RatioLimitMode.UnlimitedRatio,
            TorrentData.RatioLimitMode.SingleRatioLimit
        )

        // Should match R.array.idle_seeding_mode
        val idleSeedingModeItems = arrayOf(
            TorrentData.IdleSeedingLimitMode.GlobalIdleSeedingLimit,
            TorrentData.IdleSeedingLimitMode.UnlimitedIdleSeeding,
            TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit
        )
    }

    private val propertiesFragmentModel by TorrentPropertiesFragmentViewModel.getLazy(this)
    private val torrent: Torrent?
        get() = propertiesFragmentModel.torrent.value

    private lateinit var priorityItemValues: Array<String>
    private lateinit var ratioLimitModeItemValues: Array<String>
    private lateinit var idleSeedingModeItemValues: Array<String>

    private lateinit var doubleFilter: DoubleFilter

    private val binding by viewBinding(TorrentLimitsFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        priorityItemValues = resources.getStringArray(R.array.priority_items)
        ratioLimitModeItemValues = resources.getStringArray(R.array.ratio_limit_mode)
        idleSeedingModeItemValues = resources.getStringArray(R.array.idle_seeding_mode)
        doubleFilter = DoubleFilter(0.0..MAX_RATIO_LIMIT)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            scrollView.isEnabled = false

            downloadSpeedLimitEdit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

            uploadSpeedLimitEdit.filters = arrayOf(IntFilter(0 until MAX_SPEED_LIMIT))

            priorityView.setAdapter(ArrayDropdownAdapter(priorityItemValues))

            ratioLimitModeView.setAdapter(ArrayDropdownAdapter(ratioLimitModeItemValues))
            ratioLimitEdit.filters = arrayOf(doubleFilter)

            idleSeedingModeView.setAdapter(ArrayDropdownAdapter(idleSeedingModeItemValues))
            idleSeedingLimitEdit.filters = arrayOf(IntFilter(0..MAX_IDLE_SEEDING_LIMIT))

            maximumPeersEdit.filters = arrayOf(IntFilter(0..MAX_PEERS))

            updateView(restored = priorityView.text.isNotEmpty())

            globalLimitsCheckBox.setOnCheckedChangeListener { _, checked ->
                torrent?.setHonorSessionLimits(checked)
            }

            downloadSpeedLimitCheckBox.setDependentViews(downloadSpeedLimitLayout) { checked ->
                torrent?.setDownloadSpeedLimited(checked)
            }
            downloadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                torrent?.setDownloadSpeedLimit(it.toString().toInt())
            }

            uploadSpeedLimitCheckBox.setDependentViews(uploadSpeedLimitLayout) { checked ->
                torrent?.setUploadSpeedLimited(checked)
            }
            uploadSpeedLimitEdit.doAfterTextChangedAndNotEmpty {
                torrent?.setUploadSpeedLimit(it.toString().toInt())
            }

            priorityView.setOnItemClickListener { _, _, position, _ ->
                torrent?.setBandwidthPriority(priorityItems[position])
            }

            ratioLimitModeView.setOnItemClickListener { _, _, position, _ ->
                val mode = ratioLimitModeItems[position]
                ratioLimitLayout.isEnabled = (mode == TorrentData.RatioLimitMode.SingleRatioLimit)
                torrent?.setRatioLimitMode(mode)
            }
            ratioLimitEdit.doAfterTextChangedAndNotEmpty {
                torrent?.setRatioLimit(doubleFilter.parse(it.toString())!!)
            }

            idleSeedingModeView.setOnItemClickListener { _, _, position, _ ->
                val mode = idleSeedingModeItems[position]
                idleSeedingLimitLayout.isEnabled =
                    (mode == TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit)
                torrent?.setIdleSeedingLimitMode(mode)
            }
            idleSeedingLimitEdit.doAfterTextChangedAndNotEmpty {
                torrent?.setIdleSeedingLimit(it.toString().toInt())
            }

            maximumPeersEdit.doAfterTextChangedAndNotEmpty {
                torrent?.setPeersLimit(it.toString().toInt())
            }
        }
    }

    private fun updateView(restored: Boolean) {
        val torrent = this.torrent ?: return
        val data = torrent.data

        with(binding) {
            if (!restored) {
                globalLimitsCheckBox.isChecked = data.honorSessionLimits

                downloadSpeedLimitCheckBox.isChecked = data.downloadSpeedLimited
                downloadSpeedLimitEdit.setText(data.downloadSpeedLimit.toString())

                uploadSpeedLimitCheckBox.isChecked = data.uploadSpeedLimited
                uploadSpeedLimitEdit.setText(data.uploadSpeedLimit.toString())

                priorityView.setText(priorityItemValues[priorityItems.indexOf(data.bandwidthPriority)])

                ratioLimitModeView.setText(ratioLimitModeItemValues[ratioLimitModeItems.indexOf(data.ratioLimitMode)])
                ratioLimitEdit.setText(DecimalFormats.ratio.format(data.ratioLimit))

                idleSeedingModeView.setText(
                    idleSeedingModeItemValues[idleSeedingModeItems.indexOf(
                        data.idleSeedingLimitMode
                    )]
                )
                idleSeedingLimitEdit.setText(data.idleSeedingLimit.toString())

                maximumPeersEdit.setText(data.peersLimit.toString())
            }

            ratioLimitLayout.isEnabled =
                (ratioLimitModeView.text.toString() == ratioLimitModeItemValues[ratioLimitModeItems.indexOf(
                    TorrentData.RatioLimitMode.SingleRatioLimit
                )])
            idleSeedingLimitLayout.isEnabled =
                (idleSeedingModeView.text.toString() == idleSeedingModeItemValues[idleSeedingModeItems.indexOf(
                    TorrentData.IdleSeedingLimitMode.SingleIdleSeedingLimit
                )])
        }
    }
}
