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
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Torrent
import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.DoubleFilter
import org.equeim.tremotesf.utils.IntFilter


class TorrentLimitsFragment : Fragment() {
    private var torrent: Torrent? = null
    private var updating = false

    private var globalLimitsCheckBox: CheckBox? = null

    private var downloadSpeedLimitCheckBox: CheckBox? = null
    private var downloadSpeedLimitEdit: EditText? = null

    private var uploadSpeedLimitCheckBox: CheckBox? = null
    private var uploadSpeedLimitEdit: EditText? = null

    private var prioritySpinner: Spinner? = null

    private var ratioLimitSpinner: Spinner? = null
    private var ratioLimitEdit: EditText? = null

    private var idleSeedingSpinner: Spinner? = null
    private var idleSeedingEdit: EditText? = null

    private var maximumPeersEdit: EditText? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.torrent_limits_fragment, container, false)

        globalLimitsCheckBox = view.findViewById(R.id.global_limits_check_box) as CheckBox
        globalLimitsCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
            if (!updating) {
                torrent?.honorSessionLimits = checked
            }
        }

        downloadSpeedLimitCheckBox = view.findViewById(R.id.download_speed_limit_check_box) as CheckBox
        val downloadSpeedLimitLayout = view.findViewById(R.id.download_speed_limit_layout)
        downloadSpeedLimitLayout.isEnabled = false
        downloadSpeedLimitCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
            downloadSpeedLimitLayout.isEnabled = checked
            if (!updating) {
                torrent?.downloadSpeedLimited = checked
            }
        }
        downloadSpeedLimitEdit = view.findViewById(R.id.download_speed_limit_edit) as EditText
        downloadSpeedLimitEdit!!.filters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))
        downloadSpeedLimitEdit!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.downloadSpeedLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        uploadSpeedLimitCheckBox = view.findViewById(R.id.upload_speed_limit_check_box) as CheckBox
        val uploadSpeedLimitLayout = view.findViewById(R.id.upload_speed_limit_layout)
        uploadSpeedLimitLayout.isEnabled = false
        uploadSpeedLimitCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
            uploadSpeedLimitLayout.isEnabled = checked
            if (!updating) {
                torrent?.uploadSpeedLimited = checked
            }
        }
        uploadSpeedLimitEdit = view.findViewById(R.id.upload_speed_limit_edit) as EditText
        uploadSpeedLimitEdit!!.filters = arrayOf(IntFilter(0..(4 * 1024 * 1024 - 1)))
        uploadSpeedLimitEdit!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.uploadSpeedLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        prioritySpinner = view.findViewById(R.id.priority_spinner) as Spinner
        prioritySpinner!!.adapter = ArraySpinnerAdapter(activity,
                                                        resources.getStringArray(R.array.priority))
        prioritySpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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

        ratioLimitSpinner = view.findViewById(R.id.ratio_limit_spinner) as Spinner
        val ratioLimitSpinnerAdapter = ArrayAdapter(activity,
                                                    android.R.layout.simple_spinner_item,
                                                    resources.getStringArray(R.array.ratio_limit_mode))
        ratioLimitSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ratioLimitSpinner!!.adapter = ratioLimitSpinnerAdapter
        ratioLimitEdit = view.findViewById(R.id.ratio_limit_edit) as EditText
        ratioLimitEdit!!.filters = arrayOf(DoubleFilter(0..10000))
        ratioLimitSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                ratioLimitEdit!!.isEnabled = (position == 2)
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
        ratioLimitEdit!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.ratioLimit = DoubleFilter.parse(string.toString())!!
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        idleSeedingSpinner = view.findViewById(R.id.idle_seeding_spinner) as Spinner
        val idleSeedingSpinnerAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                                                     resources.getStringArray(R.array.idle_seeding_mode))
        idleSeedingSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        idleSeedingSpinner!!.adapter = idleSeedingSpinnerAdapter
        val idleSeedingLayout = view.findViewById(R.id.idle_seeding_layout)
        idleSeedingSpinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                idleSeedingLayout!!.isEnabled = (position == 2)
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
        idleSeedingEdit = view.findViewById(R.id.idle_seeding_edit) as EditText
        idleSeedingEdit!!.filters = arrayOf(IntFilter(0..10000))
        idleSeedingEdit!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.idleSeedingLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        maximumPeersEdit = view.findViewById(R.id.maximum_peers_edit) as EditText
        maximumPeersEdit!!.filters = arrayOf(IntFilter(0..10000))
        maximumPeersEdit!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(string: Editable) {
                if (!updating && string.isNotEmpty()) {
                    torrent?.peersLimit = string.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        update()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        globalLimitsCheckBox = null
        downloadSpeedLimitCheckBox = null
        downloadSpeedLimitEdit = null
        uploadSpeedLimitCheckBox = null
        uploadSpeedLimitEdit = null
        prioritySpinner = null
        ratioLimitSpinner = null
        ratioLimitEdit = null
        idleSeedingSpinner = null
        idleSeedingEdit = null
        maximumPeersEdit = null
    }

    fun update() {
        torrent = (activity as TorrentPropertiesActivity).torrent

        if (!isAdded || torrent == null) {
            return
        }

        updating = true

        globalLimitsCheckBox!!.isChecked = torrent!!.honorSessionLimits

        downloadSpeedLimitCheckBox!!.isChecked = torrent!!.downloadSpeedLimited
        downloadSpeedLimitEdit!!.setText(torrent!!.downloadSpeedLimit.toString())

        uploadSpeedLimitCheckBox!!.isChecked = torrent!!.uploadSpeedLimited
        uploadSpeedLimitEdit!!.setText(torrent!!.uploadSpeedLimit.toString())

        prioritySpinner!!.setSelection(when (torrent!!.bandwidthPriority) {
                                           Torrent.Priority.LOW -> 2
                                           Torrent.Priority.NORMAL -> 1
                                           Torrent.Priority.HIGH -> 0
                                           else -> 0
                                       })

        ratioLimitSpinner!!.setSelection(when (torrent!!.ratioLimitMode) {
                                             Torrent.RatioLimitMode.GLOBAL -> 0
                                             Torrent.RatioLimitMode.SINGLE -> 2
                                             Torrent.RatioLimitMode.UNLIMITED -> 1
                                             else -> 0
                                         })
        ratioLimitEdit!!.setText(DecimalFormat("0.00").format(torrent!!.ratioLimit))

        idleSeedingSpinner!!.setSelection(when (torrent!!.idleSeedingLimitMode) {
                                              Torrent.IdleSeedingLimitMode.GLOBAL -> 0
                                              Torrent.IdleSeedingLimitMode.SINGLE -> 2
                                              Torrent.IdleSeedingLimitMode.UNLIMITED -> 3
                                              else -> 0
                                          })
        idleSeedingEdit!!.setText(torrent!!.idleSeedingLimit.toString())

        maximumPeersEdit!!.setText(torrent!!.peersLimit.toString())

        updating = false
    }
}