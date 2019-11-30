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
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled
import org.equeim.tremotesf.utils.textInputLayout

import kotlinx.android.synthetic.main.server_edit_certificates_fragment.*
import kotlinx.android.synthetic.main.server_edit_fragment.*


class ServerEditFragment : NavigationFragment(R.layout.server_edit_fragment,
                                              0,
                                              R.menu.server_edit_activity_menu) {
    companion object {
        const val SERVER = "server"
    }

    private var server: Server? = null
    private lateinit var newServer: Server

    private lateinit var certificatesModel: CertificatesModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serverName = requireArguments().getString(SERVER)
        server = Servers.servers.value?.find { it.name == serverName }
        newServer = server?.copy() ?: Server()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()

        port_edit.filters = arrayOf(IntFilter(Server.portRange))

        https_check_box.isChecked = false

        certificated_button.isEnabled = false
        certificated_button.setOnClickListener {
            navController.navigate(R.id.action_serverEditFragment_to_certificatesFragment, requireArguments())
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

        if (savedInstanceState == null) {
            name_edit.setText(newServer.name)
            address_edit.setText(newServer.address)
            port_edit.setText(newServer.port.toString())
            api_path_edit.setText(newServer.apiPath)
            https_check_box.isChecked = newServer.httpsEnabled
            authentication_check_box.isChecked = newServer.authentication
            username_edit.setText(newServer.username)
            password_edit.setText(newServer.password)
            update_interval_edit.setText(newServer.updateInterval.toString())
            timeout_edit.setText(newServer.timeout.toString())
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        certificatesModel = ViewModelProvider(navController.getBackStackEntry(R.id.serverEditFragment),
                                              CertificatesModelFactory(newServer))[CertificatesModel::class.java]
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

        val nameEditText = name_edit.text.toString()

        if (nameOk &&
                addressOk &&
                portOk &&
                apiPathOk &&
                updateIntervalOk &&
                timeoutOk) {
            if (nameEditText != server?.name &&
                    Servers.servers.value?.find { it.name == nameEditText } != null) {
                navController.navigate(R.id.action_serverEditFragment_to_serverOverwriteDialogFragment)
            } else {
                save()
            }
        }

        return true
    }

    private fun setupToolbar() {
        toolbar?.apply {
            setTitle(if (server == null) R.string.add_server else R.string.edit_server)
            menu.findItem(R.id.done).setTitle(if (server == null) {
                R.string.add
            } else {
                R.string.save
            })
        }
    }

    private fun save() {
        newServer.apply {
            name = name_edit.text.toString().trim()
            address = address_edit.text.toString().trim()
            port = port_edit.text.toString().toInt()
            apiPath = api_path_edit.text.toString().trim()
            httpsEnabled = https_check_box.isChecked
            authentication = authentication_check_box.isChecked
            username = username_edit.text.toString().trim()
            password = password_edit.text.toString().trim()
            updateInterval = update_interval_edit.text.toString().toInt()
            timeout = timeout_edit.text.toString().toInt()

            certificatesModel.certificatesData.let { data ->
                selfSignedCertificateEnabled = data.selfSignedCertificateEnabled
                selfSignedCertificate = data.selfSignedCertificateData
                clientCertificateEnabled = data.clientCertificateEnabled
                clientCertificate = data.clientCertificateData
            }
        }

        val server = this.server
        if (server == null) {
            Servers.addServer(newServer)
        } else {
            Servers.setServer(server, newServer)
        }

        navController.popBackStack(R.id.serverEditFragment, true)
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

    private class CertificatesModel(server: Server) : ViewModel() {
        data class CertificatesData(var selfSignedCertificateEnabled: Boolean,
                                    var selfSignedCertificateData: String,
                                    var clientCertificateEnabled: Boolean,
                                    var clientCertificateData: String)

        val certificatesData = CertificatesData(server.selfSignedCertificateEnabled,
                                                server.selfSignedCertificate,
                                                server.clientCertificateEnabled,
                                                server.clientCertificate)
    }

    private class CertificatesModelFactory(private val server: Server) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass == CertificatesModel::class.java) {
                return CertificatesModel(server) as T
            }
            throw IllegalArgumentException()
        }
    }

    class CertificatesFragment : NavigationFragment(R.layout.server_edit_certificates_fragment,
                                                    R.string.certificates) {
        private lateinit var certificatesModel: CertificatesModel

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

            val serverName = requireArguments().getString(SERVER)
            val server = Servers.servers.value?.find { it.name == serverName }
            certificatesModel = ViewModelProvider(navController.getBackStackEntry(R.id.serverEditFragment),
                                                  CertificatesModelFactory(server ?: Server()))[CertificatesModel::class.java]
            certificatesModel.certificatesData.let { data ->
                self_signed_certificate_check_box.isChecked = data.selfSignedCertificateEnabled
                self_signed_certificate_edit.setText(data.selfSignedCertificateData)
                client_certificate_check_box.isChecked = data.clientCertificateEnabled
                client_certificate_edit.setText(data.clientCertificateData)
            }
        }

        override fun onNavigatedFrom() {
            if (view != null) {
                certificatesModel.certificatesData.apply {
                    selfSignedCertificateEnabled = self_signed_certificate_check_box.isChecked
                    selfSignedCertificateData = self_signed_certificate_edit.text.toString()
                    clientCertificateEnabled = client_certificate_check_box.isChecked
                    clientCertificateData = client_certificate_edit.text.toString()
                }
            }
        }
    }
}
