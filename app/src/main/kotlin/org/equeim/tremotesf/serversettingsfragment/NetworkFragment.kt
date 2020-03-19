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

import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Rpc
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.doAfterTextChangedAndNotEmpty

import kotlinx.android.synthetic.main.server_settings_network_fragment.*


class NetworkFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_network_fragment,
                                                            R.string.server_settings_network) {
    private companion object {
        // Should match R.array.encryption_items
        val encryptionItems = arrayOf(ServerSettingsData.EncryptionMode.AllowedEncryption,
                                      ServerSettingsData.EncryptionMode.PreferredEncryption,
                                      ServerSettingsData.EncryptionMode.RequiredEncryption)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        peer_port_edit.filters = arrayOf(IntFilter(0..65535))
        peer_port_edit.setText(Rpc.serverSettings.peerPort.toString())
        peer_port_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.peerPort = it.toString().toInt()
        }

        random_port_check_box.isChecked = Rpc.serverSettings.randomPortEnabled
        random_port_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.randomPortEnabled = checked
        }

        port_forwarding_check_box.isChecked = Rpc.serverSettings.portForwardingEnabled
        port_forwarding_check_box.setOnCheckedChangeListener { _, checked ->
            Rpc.serverSettings.portForwardingEnabled = checked
        }

        val encryptionItemValues = resources.getStringArray(R.array.encryption_items)
        encryption_view.setAdapter(ArrayDropdownAdapter(encryptionItemValues))
        encryption_view.setText(encryptionItemValues[encryptionItems.indexOf(Rpc.serverSettings.encryptionMode)])
        encryption_view.setOnItemClickListener { _, _, position, _ ->
            Rpc.serverSettings.encryptionMode = encryptionItems[position]
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
        peers_per_torrent_edit.setText(Rpc.serverSettings.maximumPeersPerTorrent.toString())
        peers_per_torrent_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.maximumPeersPerTorrent = it.toString().toInt()
        }

        peers_globally_edit.filters = arrayOf(IntFilter(0..10000))
        peers_globally_edit.setText(Rpc.serverSettings.maximumPeersGlobally.toString())
        peers_globally_edit.doAfterTextChangedAndNotEmpty {
            Rpc.serverSettings.maximumPeersGlobally = it.toString().toInt()
        }
    }
}