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

package org.equeim.tremotesf.connectionsettingsactivity

import android.app.Dialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText

import androidx.appcompat.app.AlertDialog
import androidx.core.text.trimmedLength
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit

import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.server_edit_activity_certificates_fragment.*
import kotlinx.android.synthetic.main.server_edit_activity_main_fragment.*


class ServerEditActivity : BaseActivity(false) {
    companion object {
        const val SERVER = "org.equeim.tremotesf.ServerEditActivity.SERVER"
    }

    private var server: Server? = null
    private lateinit var newServer: Server

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverName = intent.getStringExtra(SERVER)
        server = Servers.servers.find { it.name == serverName }
        newServer = server?.copy() ?: Server()

        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(android.R.id.content, MainFragment(),
                                                         MainFragment.TAG) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }

    class MainFragment : Fragment(R.layout.server_edit_activity_main_fragment) {
        companion object {
            const val TAG = "org.equeim.tremotesf.ServerEditActivity.MainFragment"
        }

        private val activity: ServerEditActivity
            get() = requireActivity() as ServerEditActivity

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setHasOptionsMenu(true)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            port_edit.filters = arrayOf(IntFilter(Server.portRange))

            https_check_box.isChecked = false

            certificates_item.isEnabled = false
            certificates_item.setChildrenEnabled(false)
            certificates_item.setOnClickListener {
                requireFragmentManager().commit {
                    replace(android.R.id.content, CertificatesFragment())
                    setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    addToBackStack(null)
                }
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
                val server = activity.newServer
                name_edit.setText(server.name)
                address_edit.setText(server.address)
                port_edit.setText(server.port.toString())
                api_path_edit.setText(server.apiPath)
                https_check_box.isChecked = server.httpsEnabled
                authentication_check_box.isChecked = server.authentication
                username_edit.setText(server.username)
                password_edit.setText(server.password)
                update_interval_edit.setText(server.updateInterval.toString())
                timeout_edit.setText(server.timeout.toString())
            }
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            activity.setTitle(if (activity.server == null) R.string.add_server else R.string.edit_server)
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.server_edit_activity_menu, menu)
            val doneMenuItem = menu.findItem(R.id.done)
            if (activity.server == null) {
                doneMenuItem.setTitle(R.string.add)
            } else {
                doneMenuItem.setTitle(R.string.save)
            }
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId != R.id.done) {
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
                if (nameEditText != activity.server?.name &&
                        Servers.servers.find { it.name == nameEditText } != null) {
                    OverwriteDialogFragment().show(requireFragmentManager(), null)
                } else {
                    save()
                }
            }

            return true
        }

        private fun save() {
            val newServer = activity.newServer
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

            if (activity.server == null) {
                Servers.addServer(newServer)
            } else {
                Servers.setServer(activity.server!!, newServer)
            }

            activity.finish()
        }

        class OverwriteDialogFragment : DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return AlertDialog.Builder(requireContext())
                        .setMessage(R.string.server_exists)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.overwrite) { _, _ ->
                            (fragmentManager?.findFragmentByTag(MainFragment.TAG) as MainFragment).save()
                        }
                        .create()
            }
        }
    }

    class CertificatesFragment : Fragment(R.layout.server_edit_activity_certificates_fragment) {
        private val activity: ServerEditActivity
            get() = requireActivity() as ServerEditActivity

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
                val newServer = activity.newServer
                self_signed_certificate_check_box.isChecked = newServer.selfSignedCertificateEnabled
                self_signed_certificate_edit.setText(newServer.selfSignedCertificate)
                client_certificate_check_box.isChecked = newServer.clientCertificateEnabled
                client_certificate_edit.setText(newServer.clientCertificate)
            }
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            activity.setTitle(R.string.certificates)
        }

        override fun onDestroyView() {
            val newServer = activity.newServer
            newServer.selfSignedCertificateEnabled = self_signed_certificate_check_box.isChecked
            newServer.selfSignedCertificate = self_signed_certificate_edit.text.toString()
            newServer.clientCertificateEnabled = client_certificate_check_box.isChecked
            newServer.clientCertificate = client_certificate_edit.text.toString()
            super.onDestroyView()
        }
    }
}