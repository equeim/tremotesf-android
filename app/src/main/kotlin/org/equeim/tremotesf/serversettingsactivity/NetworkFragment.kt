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

package org.equeim.tremotesf.serversettingsactivity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView

import androidx.fragment.app.Fragment

import org.equeim.libtremotesf.ServerSettings
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.IntFilter

import kotlinx.android.synthetic.main.server_settings_network_fragment.*


class NetworkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        requireActivity().title = getString(R.string.server_settings_network)
        return inflater.inflate(R.layout.server_settings_network_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peer_port_edit.filters = arrayOf(IntFilter(0..65535))
        peer_port_edit.setText(Rpc.instance.serverSettings.peerPort().toString())
        peer_port_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.instance.serverSettings.setPeerPort(s.toString().toInt())
                }
            }

            override fun beforeTextChanged(s: CharSequence?,
                                           start: Int,
                                           count: Int,
                                           after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        random_port_check_box.isChecked = Rpc.instance.serverSettings.isRandomPortEnabled
        random_port_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isRandomPortEnabled = checked
        }

        port_forwarding_check_box.isChecked = Rpc.instance.serverSettings.isPortForwardingEnabled
        port_forwarding_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isPortForwardingEnabled = checked
        }

        encryption_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.encryption_items),
                                                                   R.string.encryption)
        encryption_spinner.setSelection(when (Rpc.instance.serverSettings.encryptionMode()) {
                                            ServerSettings.EncryptionMode.AllowedEncryption -> 0
                                            ServerSettings.EncryptionMode.PreferredEncryption -> 1
                                            ServerSettings.EncryptionMode.RequiredEncryption -> 2
                                            else -> 0
                                        })
        encryption_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                Rpc.instance.serverSettings.setEncryptionMode(when (position) {
                    0 -> ServerSettings.EncryptionMode.AllowedEncryption
                    1 -> ServerSettings.EncryptionMode.PreferredEncryption
                    2 -> ServerSettings.EncryptionMode.RequiredEncryption
                    else -> ServerSettings.EncryptionMode.AllowedEncryption
                })
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        utp_check_box.isChecked = Rpc.instance.serverSettings.isUtpEnabled
        utp_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isUtpEnabled = checked
        }

        pex_check_box.isChecked = Rpc.instance.serverSettings.isPexEnabled
        pex_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isPexEnabled = checked
        }

        dht_check_box.isChecked = Rpc.instance.serverSettings.isDhtEnabled
        dht_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isDhtEnabled = checked
        }

        lpd_check_box.isChecked = Rpc.instance.serverSettings.isLpdEnabled
        lpd_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.instance.serverSettings.isLpdEnabled = checked
        }

        peers_per_torrent_edit.filters = arrayOf(IntFilter(0..10000))
        peers_per_torrent_edit.setText(Rpc.instance.serverSettings.maximumPeersPerTorrent().toString())
        peers_per_torrent_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.instance.serverSettings.setMaximumPeersPerTorrent(s.toString().toInt())
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
        peers_globally_edit.setText(Rpc.instance.serverSettings.maximumPeersGlobally().toString())
        peers_globally_edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty()) {
                    Rpc.instance.serverSettings.setMaximumPeersGlobally(s.toString().toInt())
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