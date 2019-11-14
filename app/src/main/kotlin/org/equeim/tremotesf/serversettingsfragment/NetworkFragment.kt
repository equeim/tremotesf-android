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
import android.widget.AdapterView

import org.equeim.libtremotesf.ServerSettings
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.ArraySpinnerAdapterWithHeader
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_network_fragment.*


class NetworkFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_network_fragment,
                                                            R.string.server_settings_network) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peer_port_edit.filters = arrayOf(IntFilter(0..65535))
        peer_port_edit.setText(Rpc.serverSettings.peerPort().toString())
        peer_port_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setPeerPort(it.toString().toInt())
        }

        random_port_check_box.isChecked = Rpc.serverSettings.isRandomPortEnabled
        random_port_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isRandomPortEnabled = checked
        }

        port_forwarding_check_box.isChecked = Rpc.serverSettings.isPortForwardingEnabled
        port_forwarding_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isPortForwardingEnabled = checked
        }

        encryption_spinner.adapter = ArraySpinnerAdapterWithHeader(resources.getStringArray(R.array.encryption_items),
                                                                   R.string.encryption)
        encryption_spinner.setSelection(when (Rpc.serverSettings.encryptionMode()) {
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
                Rpc.serverSettings.setEncryptionMode(when (position) {
                    0 -> ServerSettings.EncryptionMode.AllowedEncryption
                    1 -> ServerSettings.EncryptionMode.PreferredEncryption
                    2 -> ServerSettings.EncryptionMode.RequiredEncryption
                    else -> ServerSettings.EncryptionMode.AllowedEncryption
                })
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        utp_check_box.isChecked = Rpc.serverSettings.isUtpEnabled
        utp_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isUtpEnabled = checked
        }

        pex_check_box.isChecked = Rpc.serverSettings.isPexEnabled
        pex_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isPexEnabled = checked
        }

        dht_check_box.isChecked = Rpc.serverSettings.isDhtEnabled
        dht_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isDhtEnabled = checked
        }

        lpd_check_box.isChecked = Rpc.serverSettings.isLpdEnabled
        lpd_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.isLpdEnabled = checked
        }

        peers_per_torrent_edit.filters = arrayOf(IntFilter(0..10000))
        peers_per_torrent_edit.setText(Rpc.serverSettings.maximumPeersPerTorrent().toString())
        peers_per_torrent_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setMaximumPeersPerTorrent(it.toString().toInt())
        }

        peers_globally_edit.filters = arrayOf(IntFilter(0..10000))
        peers_globally_edit.setText(Rpc.serverSettings.maximumPeersGlobally().toString())
        peers_globally_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.setMaximumPeersGlobally(it.toString().toInt())
        }
    }
}