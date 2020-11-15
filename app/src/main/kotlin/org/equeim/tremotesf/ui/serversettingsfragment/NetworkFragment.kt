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

package org.equeim.tremotesf.ui.serversettingsfragment

import android.os.Bundle
import android.view.View

import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.Rpc
import org.equeim.tremotesf.databinding.ServerSettingsNetworkFragmentBinding
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.viewBinding


class NetworkFragment : ServerSettingsFragment.BaseFragment(R.layout.server_settings_network_fragment,
                                                            R.string.server_settings_network) {
    private companion object {
        // Should match R.array.encryption_items
        val encryptionItems = arrayOf(ServerSettingsData.EncryptionMode.AllowedEncryption,
                                      ServerSettingsData.EncryptionMode.PreferredEncryption,
                                      ServerSettingsData.EncryptionMode.RequiredEncryption)
    }

    private val binding by viewBinding(ServerSettingsNetworkFragmentBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with (binding) {
            peerPortEdit.filters = arrayOf(IntFilter(0..65535))
            peerPortEdit.setText(Rpc.serverSettings.peerPort.toString())
            peerPortEdit.doAfterTextChangedAndNotEmpty {
                Rpc.serverSettings.peerPort = it.toString().toInt()
            }

            randomPortCheckBox.isChecked = Rpc.serverSettings.randomPortEnabled
            randomPortCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.randomPortEnabled = checked
            }

            portForwardingCheckBox.isChecked = Rpc.serverSettings.portForwardingEnabled
            portForwardingCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.portForwardingEnabled = checked
            }

            val encryptionItemValues = resources.getStringArray(R.array.encryption_items)
            encryptionView.setAdapter(ArrayDropdownAdapter(encryptionItemValues))
            encryptionView.setText(encryptionItemValues[encryptionItems.indexOf(Rpc.serverSettings.encryptionMode)])
            encryptionView.setOnItemClickListener { _, _, position, _ ->
                Rpc.serverSettings.encryptionMode = encryptionItems[position]
            }

            utpCheckBox.isChecked = Rpc.serverSettings.utpEnabled
            utpCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.utpEnabled = checked
            }

            pexCheckBox.isChecked = Rpc.serverSettings.pexEnabled
            pexCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.pexEnabled = checked
            }

            dhtCheckBox.isChecked = Rpc.serverSettings.dhtEnabled
            dhtCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.dhtEnabled = checked
            }

            lpdCheckBox.isChecked = Rpc.serverSettings.lpdEnabled
            lpdCheckBox.setOnCheckedChangeListener { _, checked ->
                Rpc.serverSettings.lpdEnabled = checked
            }

            peersPerTorrentEdit.filters = arrayOf(IntFilter(0..10000))
            peersPerTorrentEdit.setText(Rpc.serverSettings.maximumPeersPerTorrent.toString())
            peersPerTorrentEdit.doAfterTextChangedAndNotEmpty {
                Rpc.serverSettings.maximumPeersPerTorrent = it.toString().toInt()
            }

            peersGloballyEdit.filters = arrayOf(IntFilter(0..10000))
            peersGloballyEdit.setText(Rpc.serverSettings.maximumPeersGlobally.toString())
            peersGloballyEdit.doAfterTextChangedAndNotEmpty {
                Rpc.serverSettings.maximumPeersGlobally = it.toString().toInt()
            }
        }
    }
}