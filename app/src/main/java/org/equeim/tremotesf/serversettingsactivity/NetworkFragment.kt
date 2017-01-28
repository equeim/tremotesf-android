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

import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.ServerSettings
import org.equeim.tremotesf.utils.ArraySpinnerAdapter
import org.equeim.tremotesf.utils.IntFilter


class NetworkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_network)

        val view = inflater.inflate(R.layout.server_settings_network_fragment, container, false)

        val peerPortEdit = view.findViewById(R.id.peer_port_edit) as EditText
        peerPortEdit.filters = arrayOf(IntFilter(0..65535))
        peerPortEdit.setText(Rpc.serverSettings.peerPort.toString())
        peerPortEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.peerPort = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val randomPortCheckBox = view.findViewById(R.id.random_port_checkbox) as CheckBox
        randomPortCheckBox.isChecked = Rpc.serverSettings.randomPortEnabled
        randomPortCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.randomPortEnabled = checked
        }

        val portForwardingCheckBox = view.findViewById(R.id.port_forwarding_checkbox) as CheckBox
        portForwardingCheckBox.isChecked = Rpc.serverSettings.portForwardingEnabled
        portForwardingCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.portForwardingEnabled = checked
        }

        val encryptionSpinner = view.findViewById(R.id.encryption_spinner) as Spinner
        encryptionSpinner.adapter = ArraySpinnerAdapter(activity, resources.getStringArray(R.array.encryption))
        encryptionSpinner.setSelection(when (Rpc.serverSettings.encryption) {
                                           ServerSettings.Encryption.ALLOWED -> 0
                                           ServerSettings.Encryption.PREFERRED -> 1
                                           ServerSettings.Encryption.REQUIRED -> 2
                                           else -> 0
                                       })
        encryptionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Rpc.serverSettings.encryption = when (position) {
                    0 -> ServerSettings.Encryption.ALLOWED
                    1 -> ServerSettings.Encryption.PREFERRED
                    2 -> ServerSettings.Encryption.REQUIRED
                    else -> ServerSettings.Encryption.ALLOWED
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val utpCheckBox = view.findViewById(R.id.utp_checkbox) as CheckBox
        utpCheckBox.isChecked = Rpc.serverSettings.utpEnabled
        utpCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.utpEnabled = checked
        }

        val pexCheckBox = view.findViewById(R.id.pex_checkbox) as CheckBox
        pexCheckBox.isChecked = Rpc.serverSettings.pexEnabled
        pexCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.pexEnabled = checked
        }

        val dhtCheckBox = view.findViewById(R.id.dht_checkbox) as CheckBox
        dhtCheckBox.isChecked = Rpc.serverSettings.dhtEnabled
        dhtCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.dhtEnabled = checked
        }

        val lpdCheckBox = view.findViewById(R.id.lpd_checkbox) as CheckBox
        lpdCheckBox.isChecked = Rpc.serverSettings.lpdEnabled
        lpdCheckBox.setOnCheckedChangeListener { checkBox, checked ->
            Rpc.serverSettings.lpdEnabled = checked
        }

        val peerPerTorrentEdit = view.findViewById(R.id.peers_per_torrent_edit) as EditText
        peerPerTorrentEdit.filters = arrayOf(IntFilter(0..10000))
        peerPerTorrentEdit.setText(Rpc.serverSettings.peersLimitPerTorrent.toString())
        peerPerTorrentEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.peersLimitPerTorrent = s.toString().toInt()
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val peerGloballyEdit = view.findViewById(R.id.peers_globally_edit) as EditText
        peerGloballyEdit.filters = arrayOf(IntFilter(0..10000))
        peerGloballyEdit.setText(Rpc.serverSettings.peersLimitGlobal.toString())
        peerGloballyEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.serverSettings.peersLimitGlobal = s.toString().toInt()
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