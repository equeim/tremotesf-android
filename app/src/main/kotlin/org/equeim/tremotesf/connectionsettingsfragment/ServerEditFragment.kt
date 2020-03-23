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

package org.equeim.tremotesf.connectionsettingsfragment

import android.app.Dialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText

import androidx.core.text.trimmedLength
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.NavigationDialogFragment
import org.equeim.tremotesf.NavigationFragment
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.textInputLayout

import kotlinx.android.synthetic.main.server_edit_certificates_fragment.*
import kotlinx.android.synthetic.main.server_edit_fragment.*
import kotlinx.android.synthetic.main.server_edit_fragment.address_edit
import kotlinx.android.synthetic.main.server_edit_fragment.password_edit
import kotlinx.android.synthetic.main.server_edit_fragment.password_edit_layout
import kotlinx.android.synthetic.main.server_edit_fragment.port_edit
import kotlinx.android.synthetic.main.server_edit_fragment.username_edit
import kotlinx.android.synthetic.main.server_edit_fragment.username_edit_layout
import kotlinx.android.synthetic.main.server_edit_proxy_fragment.*


class ServerEditFragment : NavigationFragment(R.layout.server_edit_fragment,
                                              0,
                                              R.menu.server_edit_activity_menu) {
    companion object {
        const val SERVER = "server"
    }

    private lateinit var model: Model

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        port_edit.filters = arrayOf(IntFilter(Server.portRange))

        proxy_settings_button.setOnClickListener {
            navigate(R.id.action_serverEditFragment_to_proxySettingsFragment, requireArguments())
        }

        https_check_box.isChecked = false

        certificated_button.isEnabled = false
        certificated_button.setOnClickListener {
            navigate(R.id.action_serverEditFragment_to_certificatesFragment, requireArguments())
        }
        https_check_box.setOnCheckedChangeListener { _, checked ->
            certificated_button.isEnabled = checked
        }

        authentication_check_box.isChecked = false

        username_edit_layout.isEnabled = false
        password_edit_layout.isEnabled = false
        authentication_check_box.setOnCheckedChangeListener { _, checked ->
            username_edit_layout.isEnabled = checked
            password_edit_layout.isEnabled = checked
        }

        update_interval_edit.filters = arrayOf(IntFilter(Server.updateIntervalRange))
        timeout_edit.filters = arrayOf(IntFilter(Server.timeoutRange))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        model = ViewModelProvider(navController.getBackStackEntry(R.id.serverEditFragment),
                                  ModelFactory(requireArguments().getString(SERVER)))[Model::class.java]

        setupToolbar()

        if (savedInstanceState == null) {
            with (model.server) {
                name_edit.setText(name)
                address_edit.setText(address)
                port_edit.setText(port.toString())
                api_path_edit.setText(apiPath)
                https_check_box.isChecked = httpsEnabled
                authentication_check_box.isChecked = authentication
                username_edit.setText(username)
                password_edit.setText(password)
                update_interval_edit.setText(updateInterval.toString())
                timeout_edit.setText(timeout.toString())
            }
        }
    }

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.done) {
            return false
        }

        val error = getString(R.string.empty_field_error)
        val checkLength: (EditText) -> Boolean = { edit ->
            val ret: Boolean
            edit.textInputLayout.error = if (edit.text.trimmedLength() == 0) {
                ret = false
                error
            } else {
                ret = true
                null
            }
            ret
        }

        val nameOk = checkLength(name_edit)
        val addressOk = checkLength(address_edit)
        val portOk = checkLength(port_edit)
        val apiPathOk = checkLength(api_path_edit)
        val updateIntervalOk = checkLength(update_interval_edit)
        val timeoutOk = checkLength(timeout_edit)

        val nameEditText = name_edit.text?.toString() ?: ""

        if (nameOk &&
                addressOk &&
                portOk &&
                apiPathOk &&
                updateIntervalOk &&
                timeoutOk) {
            if (nameEditText != model.existingServer?.name &&
                    Servers.servers.value.find { it.name == nameEditText } != null) {
                navigate(R.id.action_serverEditFragment_to_serverOverwriteDialogFragment)
            } else {
                save()
            }
        }

        return true
    }

    private fun setupToolbar() {
        toolbar?.apply {
            setTitle(if (model.existingServer == null) R.string.add_server else R.string.edit_server)
            menu.findItem(R.id.done).setTitle(if (model.existingServer == null) {
                R.string.add
            } else {
                R.string.save
            })
        }
    }

    private fun save() {
        model.server.apply {
            name = name_edit.text?.toString()?.trim() ?: ""
            address = address_edit.text?.toString()?.trim() ?: ""
            port = port_edit.text?.toString()?.toInt() ?: 0
            apiPath = api_path_edit.text?.toString()?.trim() ?: ""
            httpsEnabled = https_check_box.isChecked
            authentication = authentication_check_box.isChecked
            username = username_edit.text?.toString()?.trim() ?: ""
            password = password_edit.text?.toString()?.trim() ?: ""
            updateInterval = update_interval_edit.text?.toString()?.toInt() ?: 0
            timeout = timeout_edit.text?.toString()?.toInt() ?: 0
        }

        model.existingServer.let { existing ->
            if (existing == null) {
                Servers.addServer(model.server)
            } else {
                Servers.setServer(existing, model.server)
            }
        }

        navController.popBackStack(R.id.serverEditFragment, true)
    }

    private class Model(serverName: String?) : ViewModel() {
        val existingServer = if (serverName != null) Servers.servers.value.find { it.name == serverName } else null
        val server = existingServer?.copy() ?: Server()
    }

    private class ModelFactory(private val serverName: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == Model::class.java) {
                return Model(serverName) as T
            }
            throw IllegalArgumentException()
        }
    }

    class ServerOverwriteDialogFragment : NavigationDialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.server_exists)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.overwrite) { _, _ ->
                        (parentFragmentManager.primaryNavigationFragment as? ServerEditFragment)?.save()
                    }
                    .create()
        }
    }

    class CertificatesFragment : NavigationFragment(R.layout.server_edit_certificates_fragment,
                                                    R.string.certificates) {
        private lateinit var model: Model

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            self_signed_certificate_check_box.setOnCheckedChangeListener { _, checked ->
                self_signed_certificate_layout.isEnabled = checked
            }
            self_signed_certificate_layout.isEnabled = false
            client_certificate_check_box.setOnCheckedChangeListener { _, checked ->
                client_certificate_layout.isEnabled = checked
            }
            client_certificate_layout.isEnabled = false
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            model = ViewModelProvider(navController.getBackStackEntry(R.id.serverEditFragment),
                                      ModelFactory(requireArguments().getString(SERVER)))[Model::class.java]
            with(model.server) {
                self_signed_certificate_check_box.isChecked = selfSignedCertificateEnabled
                self_signed_certificate_edit.setText(selfSignedCertificate)
                client_certificate_check_box.isChecked = clientCertificateEnabled
                client_certificate_edit.setText(clientCertificate)
            }
        }

        override fun onNavigatedFrom() {
            if (view != null) {
                model.server.apply {
                    selfSignedCertificateEnabled = self_signed_certificate_check_box.isChecked
                    selfSignedCertificate = self_signed_certificate_edit.text?.toString() ?: ""
                    clientCertificateEnabled = client_certificate_check_box.isChecked
                    clientCertificate = client_certificate_edit.text?.toString() ?: ""
                }
            }
        }
    }

    class ProxySettingsFragment : NavigationFragment(R.layout.server_edit_proxy_fragment,
                                                     R.string.proxy_settings) {
        private companion object {
            // Should match R.array.proxy_type_items
            val proxyTypeItems = arrayOf(org.equeim.libtremotesf.Server.ProxyType.Default,
                                         org.equeim.libtremotesf.Server.ProxyType.Http,
                                         org.equeim.libtremotesf.Server.ProxyType.Socks5)
        }

        private lateinit var model: Model
        private lateinit var proxyTypeItemValues: Array<String>

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)

            proxyTypeItemValues = resources.getStringArray(R.array.proxy_type_items)
            proxy_type_view.setAdapter(ArrayDropdownAdapter(proxyTypeItemValues))
            proxy_type_view.setOnItemClickListener { _, _, position, _ ->
                update(proxyTypeItems[position] != org.equeim.libtremotesf.Server.ProxyType.Default)
            }

            port_edit.filters = arrayOf(IntFilter(Server.portRange))

            model = ViewModelProvider(navController.getBackStackEntry(R.id.serverEditFragment),
                                      ModelFactory(requireArguments().getString(SERVER)))[Model::class.java]
            with(model.server) {
                proxy_type_view.setText(proxyTypeItemValues[proxyTypeItems.indexOf(nativeProxyType())])
                address_edit.setText(proxyHostname)
                port_edit.setText(proxyPort.toString())
                username_edit.setText(proxyUser)
                password_edit.setText(proxyPassword)

                if (nativeProxyType() == org.equeim.libtremotesf.Server.ProxyType.Default) {
                    update(false)
                }
            }
        }

        override fun onNavigatedFrom() {
            if (view != null) {
                model.server.apply {
                    proxyType = Server.fromNativeProxyType(proxyTypeItems[proxyTypeItemValues.indexOf(proxy_type_view.text.toString())])
                    proxyHostname = address_edit.text?.toString() ?: ""
                    proxyPort = port_edit.text?.toString()?.toInt() ?: 0
                    proxyUser = username_edit.text?.toString() ?: ""
                    proxyPassword = password_edit.text?.toString() ?: ""
                }
            }
        }

        private fun update(enabled: Boolean) {
            address_edit_layout.isEnabled = enabled
            port_edit_layout.isEnabled = enabled
            username_edit_layout.isEnabled = enabled
            password_edit_layout.isEnabled = enabled
        }
    }
}
