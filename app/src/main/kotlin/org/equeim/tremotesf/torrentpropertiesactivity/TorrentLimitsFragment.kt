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

package org.equeim.tremotesf.torrentpropertiesactivity

import java.text.DecimalFormat

import android.app.Fragment
import android.os.Bundle

import android.text.Editable
import android.text.TextWatcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import android.widget.AdapterView

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter

import kotlinx.android.synthetic.main.torrent_limits_fragment.*


class TorrentLimitsFragment : Fragment() {
    private var torrent: Torrent? = null
    private var updating = false

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.torrent_limits_fragment, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        global_limits_check_box.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                torrent?.honorSessionLimits = checked
            }
        }

        download_speed_limit_layout.isEnabled = false
        download_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            download_speed_limit_layout.isEnabled = checked
            if (!updating) {
                torrent?.downloadSpeedLimited = checked
            }
        }

        download_speed_limit_edit.filters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))
        download_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.downloadSpeedLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        upload_speed_limit_layout.isEnabled = false
        upload_speed_limit_check_box.setOnCheckedChangeListener { _, checked ->
            upload_speed_limit_layout.isEnabled = checked
            if (!updating) {
                torrent?.uploadSpeedLimited = checked
            }
        }

        upload_speed_limit_edit.filters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))
        upload_speed_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.uploadSpeedLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        priority_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.priority_items),
                                                                 R.string.priority)
        priority_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                if (!updating) {
                    torrent?.bandwidthPriority = when (position) {
                        0 -> Torrent.Priority.HIGH
                        1 -> Torrent.Priority.NORMAL
                        2 -> Torrent.Priority.LOW
                        else -> Torrent.Priority.NORMAL
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        ratio_limit_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.ratio_limit_mode),
                                                                    R.string.ratio_limit)
        ratio_limit_edit.filters = arrayOf(DoubleFilter(0..10000))
        ratio_limit_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                ratio_limit_edit.isEnabled = (position == 2)
                if (!updating) {
                    torrent?.ratioLimitMode = when (position) {
                        0 -> Torrent.RatioLimitMode.GLOBAL
                        1 -> Torrent.RatioLimitMode.UNLIMITED
                        2 -> Torrent.RatioLimitMode.SINGLE
                        else -> Torrent.RatioLimitMode.GLOBAL
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        ratio_limit_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.ratioLimit = DoubleFilter.parse(string.toString())!!
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        idle_seeding_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.idle_seeding_mode),
                                                                     R.string.idle_seeding)
        idle_seeding_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                idle_seeding_layout.isEnabled = (position == 2)
                if (!updating) {
                    torrent?.idleSeedingLimitMode = when (position) {
                        0 -> Torrent.IdleSeedingLimitMode.GLOBAL
                        1 -> Torrent.IdleSeedingLimitMode.UNLIMITED
                        2 -> Torrent.IdleSeedingLimitMode.SINGLE
                        else -> Torrent.IdleSeedingLimitMode.GLOBAL
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        idle_seeding_edit.filters = arrayOf(IntFilter(0..10000))
        idle_seeding_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.idleSeedingLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        maximum_peers_edit.filters = arrayOf(IntFilter(0..10000))
        maximum_peers_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.peersLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        update()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    fun update() {
        torrent = (activity as TorrentPropertiesActivity).torrent

        if (!isAdded || torrent == null) {
            return
        }

        updating = true

        global_limits_check_box.isChecked = torrent!!.honorSessionLimits

        download_speed_limit_check_box.isChecked = torrent!!.downloadSpeedLimited
        download_speed_limit_edit.setText(torrent!!.downloadSpeedLimit.toString())

        upload_speed_limit_check_box.isChecked = torrent!!.uploadSpeedLimited
        upload_speed_limit_edit.setText(torrent!!.uploadSpeedLimit.toString())

        priority_spinner.setSelection(when (torrent!!.bandwidthPriority) {
                                          Torrent.Priority.LOW -> 2
                                          Torrent.Priority.NORMAL -> 1
                                          Torrent.Priority.HIGH -> 0
                                          else -> 0
                                      })

        ratio_limit_spinner.setSelection(when (torrent!!.ratioLimitMode) {
                                             Torrent.RatioLimitMode.GLOBAL -> 0
                                             Torrent.RatioLimitMode.SINGLE -> 2
                                             Torrent.RatioLimitMode.UNLIMITED -> 1
                                             else -> 0
                                         })
        ratio_limit_edit.setText(DecimalFormat("0.00").format(torrent!!.ratioLimit))

        idle_seeding_spinner.setSelection(when (torrent!!.idleSeedingLimitMode) {
                                              Torrent.IdleSeedingLimitMode.GLOBAL -> 0
                                              Torrent.IdleSeedingLimitMode.SINGLE -> 2
                                              Torrent.IdleSeedingLimitMode.UNLIMITED -> 3
                                              else -> 0
                                          })
        idle_seeding_edit.setText(torrent!!.idleSeedingLimit.toString())

        maximum_peers_edit.setText(torrent!!.peersLimit.toString())

        updating = false
    }
}