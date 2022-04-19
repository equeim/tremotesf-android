/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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
import org.equeim.libtremotesf.ServerSettingsData
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerSettingsNetworkFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.IntFilter
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import timber.log.Timber


class NetworkFragment : ServerSettingsFragment.BaseFragment(
    R.layout.server_settings_network_fragment,
    R.string.server_settings_network
) {
    private companion object {
        // Should match R.array.encryption_items
        val encryptionItems = arrayOf(
            ServerSettingsData.EncryptionMode.AllowedEncryption,
            ServerSettingsData.EncryptionMode.PreferredEncryption,
            ServerSettingsData.EncryptionMode.RequiredEncryption
        )
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        with(ServerSettingsNetworkFragmentBinding.bind(requireView())) {
            peerPortEdit.filters = arrayOf(IntFilter(0..65535))
            peerPortEdit.setText(GlobalRpc.serverSettings.peerPort.toString())
            peerPortEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.peerPort = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse peer port $it")
                }
            }

            randomPortCheckBox.isChecked = GlobalRpc.serverSettings.randomPortEnabled
            randomPortCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.randomPortEnabled = checked
            }

            portForwardingCheckBox.isChecked = GlobalRpc.serverSettings.portForwardingEnabled
            portForwardingCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.portForwardingEnabled = checked
            }

            val encryptionItemValues = resources.getStringArray(R.array.encryption_items)
            encryptionView.setAdapter(ArrayDropdownAdapter(encryptionItemValues))
            encryptionView.setText(encryptionItemValues[encryptionItems.indexOf(GlobalRpc.serverSettings.encryptionMode)])
            encryptionView.setOnItemClickListener { _, _, position, _ ->
                GlobalRpc.serverSettings.encryptionMode = encryptionItems[position]
            }

            utpCheckBox.isChecked = GlobalRpc.serverSettings.utpEnabled
            utpCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.utpEnabled = checked
            }

            pexCheckBox.isChecked = GlobalRpc.serverSettings.pexEnabled
            pexCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.pexEnabled = checked
            }

            dhtCheckBox.isChecked = GlobalRpc.serverSettings.dhtEnabled
            dhtCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.dhtEnabled = checked
            }

            lpdCheckBox.isChecked = GlobalRpc.serverSettings.lpdEnabled
            lpdCheckBox.setOnCheckedChangeListener { _, checked ->
                GlobalRpc.serverSettings.lpdEnabled = checked
            }

            peersPerTorrentEdit.filters = arrayOf(IntFilter(0..10000))
            peersPerTorrentEdit.setText(GlobalRpc.serverSettings.maximumPeersPerTorrent.toString())
            peersPerTorrentEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.maximumPeersPerTorrent = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse maximum peers count $it")
                }
            }

            peersGloballyEdit.filters = arrayOf(IntFilter(0..10000))
            peersGloballyEdit.setText(GlobalRpc.serverSettings.maximumPeersGlobally.toString())
            peersGloballyEdit.doAfterTextChangedAndNotEmpty {
                try {
                    GlobalRpc.serverSettings.maximumPeersGlobally = it.toString().toInt()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse maximum peers count $it")
                }
            }
        }
    }
}