/*
 * Copyright (C) 2017-2018 Alexey Rochev <equeim@gmail.com>
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

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.ServerSettings

import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.IntFilter

import kotlinx.android.synthetic.main.server_settings_network_fragment.*


class NetworkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        activity.title = getString(R.string.server_settings_network)
        return inflater.inflate(R.layout.server_settings_network_fragment, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peer_port_edit.filters = arrayOf(IntFilter(0..65535))
        peer_port_edit.setText(Rpc.serverSettings.peerPort.toString())
        peer_port_edit.addTextChangedListener(object : TextWatcher {
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

        random_port_check_box.isChecked = Rpc.serverSettings.randomPortEnabled
        random_port_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.randomPortEnabled = checked
        }

        port_forwarding_check_box.isChecked = Rpc.serverSettings.portForwardingEnabled
        port_forwarding_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.portForwardingEnabled = checked
        }

        encryption_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.encryption_items),
                                                                   R.string.encryption)
        encryption_spinner.setSelection(when (Rpc.serverSettings.encryption) {
                                            ServerSettings.Encryption.ALLOWED -> 0
                                            ServerSettings.Encryption.PREFERRED -> 1
                                            ServerSettings.Encryption.REQUIRED -> 2
                                            else -> 0
                                        })
        encryption_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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

        utp_check_box.isChecked = Rpc.serverSettings.utpEnabled
        utp_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.utpEnabled = checked
        }

        pex_check_box.isChecked = Rpc.serverSettings.pexEnabled
        pex_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.pexEnabled = checked
        }

        dht_check_box.isChecked = Rpc.serverSettings.dhtEnabled
        dht_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.dhtEnabled = checked
        }

        lpd_check_box.isChecked = Rpc.serverSettings.lpdEnabled
        lpd_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.lpdEnabled = checked
        }

        peers_per_torrent_edit.filters = arrayOf(IntFilter(0..10000))
        peers_per_torrent_edit.setText(Rpc.serverSettings.peersLimitPerTorrent.toString())
        peers_per_torrent_edit.addTextChangedListener(object : TextWatcher {
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

        peers_globally_edit.filters = arrayOf(IntFilter(0..10000))
        peers_globally_edit.setText(Rpc.serverSettings.peersLimitGlobal.toString())
        peers_globally_edit.addTextChangedListener(object : TextWatcher {
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as ServerSettingsActivity).hideKeyboard()
    }
}