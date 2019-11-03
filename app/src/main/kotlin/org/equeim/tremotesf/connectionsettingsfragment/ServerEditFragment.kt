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

import androidx.appcompat.app.AlertDialog
import androidx.core.text.trimmedLength
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController

import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.server_edit_certificates_fragment.*
import kotlinx.android.synthetic.main.server_edit_fragment.*
import org.equeim.tremotesf.NavigationFragment


class ServerEditFragment : NavigationFragment(R.layout.server_edit_fragment,
                                              0,
                                              R.menu.server_edit_activity_menu) {
    companion object {
        const val SERVER = "server"
    }

    private var server: Server? = null
    private lateinit var newServer: Server

    private val certificatesModel: CertificatesModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverName = requireArguments().getString(SERVER)
        server = Servers.servers.find { it.name == serverName }
        newServer = server?.copy() ?: Server()

        certificatesModel.certificatesData.apply {
            if (value == null) {
                value = CertificatesModel.CertificatesData(newServer.selfSignedCertificateEnabled,
                                                           newServer.selfSignedCertificate,
                                                           newServer.clientCertificateEnabled,
                                                           newServer.clientCertificate)
            }
            observe(this@ServerEditFragment) { data: CertificatesModel.CertificatesData? ->
                if (data != null) {
                    newServer.selfSignedCertificateEnabled = data.selfSignedCertificateEnabled
                    newServer.selfSignedCertificate = data.selfSignedCertificateData
                    newServer.clientCertificateEnabled = data.clientCertificateEnabled
                    newServer.clientCertificate = data.clientCertificateData
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()

        port_edit.filters = arrayOf(IntFilter(Server.portRange))

        https_check_box.isChecked = false

        certificates_item.isEnabled = false
        certificates_item.setChildrenEnabled(false)
        certificates_item.setOnClickListener {
            findNavController().navigate(R.id.action_serverEditFragment_to_certificatesFragment)
        }
        https_check_box.setOnCheckedChangeListener { _, checked ->
            certificates_item.isEnabled = checked
            certificates_item.setChildrenEnabled(checked)
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

    override fun onToolbarMenuItemClicked(menuItem: MenuItem): Boolean {
        if (menuItem.itemId != R.id.done) {
            return false
        }

        val error = getString(R.string.empty_field_error)
        val checkLength = fun(edit: EditText): Boolean {
            if (edit.text.trimmedLength() == 0) {
                edit.error = error
                return false
            }
            return true
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
                    Servers.servers.find { it.name == nameEditText } != null) {
                OverwriteDialogFragment().show(requireFragmentManager(), null)
            } else {
                save()
            }
        }

        return true
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
        }

        val server = this.server
        if (server == null) {
            Servers.addServer(newServer)
        } else {
            Servers.setServer(server, newServer)
        }

        certificatesModel.certificatesData.value = null

        activity?.onBackPressed()
    }

    class OverwriteDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireContext())
                    .setMessage(R.string.server_exists)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.overwrite) { _, _ ->
                        (parentFragment as? ServerEditFragment)?.save()
                    }
                    .create()
        }
    }

    class CertificatesModel : ViewModel() {
        data class CertificatesData(val selfSignedCertificateEnabled: Boolean,
                                    val selfSignedCertificateData: String,
                                    val clientCertificateEnabled: Boolean,
                                    val clientCertificateData: String)

        val certificatesData = MutableLiveData<CertificatesData>()
    }

    class CertificatesFragment : NavigationFragment(R.layout.server_edit_certificates_fragment,
                                                    R.string.certificates) {
        private val certificatesModel: CertificatesModel by activityViewModels()

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            self_signed_certificate_check_box.isChecked = false

            self_signed_certificate_layout.isEnabled = false
            self_signed_certificate_check_box.setOnCheckedChangeListener { _, checked ->
                self_signed_certificate_layout.isEnabled = checked
            }

            client_certificate_check_box.isChecked = false

            client_certificate_layout.isEnabled = false
            client_certificate_check_box.setOnCheckedChangeListener { _, checked ->
                client_certificate_layout.isEnabled = checked
            }

            if (savedInstanceState == null) {
                certificatesModel.certificatesData.value?.let { data ->
                    self_signed_certificate_check_box.isChecked = data.selfSignedCertificateEnabled
                    self_signed_certificate_edit.setText(data.selfSignedCertificateData)
                    client_certificate_check_box.isChecked = data.clientCertificateEnabled
                    client_certificate_edit.setText(data.clientCertificateData)
                }
            }
        }

        override fun onNavigatedFrom() {
            if (view != null) {
                certificatesModel.certificatesData.value =
                        CertificatesModel.CertificatesData(self_signed_certificate_check_box.isChecked,
                                                           self_signed_certificate_edit.text.toString(),
                                                           client_certificate_check_box.isChecked,
                                                           client_certificate_edit.text.toString())
            }
        }
    }
}
