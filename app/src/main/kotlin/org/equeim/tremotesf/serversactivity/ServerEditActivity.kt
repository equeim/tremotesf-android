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

package org.equeim.tremotesf.serversactivity

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog

import androidx.core.text.trimmedLength

import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled

import kotlinx.android.synthetic.main.server_edit_activity_main_fragment.*
import kotlinx.android.synthetic.main.server_edit_activity_certificates_fragment.*


class ServerEditActivity : BaseActivity() {
    companion object {
        const val SERVER = "org.equeim.tremotesf.ServerEditActivity.SERVER"
    }

    private var server: Server? = null
    private val newServer = Server()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(Settings.theme)

        val serverName = intent.getStringExtra(SERVER)
        server = Servers.servers.find { it.name == serverName }
        if (server != null) {
            server!!.copyTo(newServer)
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, MainFragment(), MainFragment.TAG)
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }

    class MainFragment : Fragment() {
        companion object {
            const val TAG = "org.equeim.tremotesf.ServerEditActivity.MainFragment"
        }

        private val activity: ServerEditActivity
            get() {
                return getActivity() as ServerEditActivity
            }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setHasOptionsMenu(true)
        }

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            if (activity.server == null) {
                activity.title = getString(R.string.add_server)
            } else {
                activity.title = getString(R.string.edit_server)
            }

            return inflater.inflate(R.layout.server_edit_activity_main_fragment, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            port_edit.filters = arrayOf(IntFilter(0..65535))

            https_check_box.isChecked = false

            certificates_item.isEnabled = false
            certificates_item.setChildrenEnabled(false)
            certificates_item.setOnClickListener {
                requireFragmentManager()
                        .beginTransaction()
                        .replace(android.R.id.content, CertificatesFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .addToBackStack(null)
                        .commit()
            }
            https_check_box.setOnCheckedChangeListener { _, checked ->
                certificates_item.isEnabled = checked
                certificates_item.setChildrenEnabled(checked)
            }

            authentication_check_box.isChecked = false

            username_edit.isEnabled = false
            password_edit.isEnabled = false
            authentication_check_box.setOnCheckedChangeListener { _, checked ->
                username_edit.isEnabled = checked
                password_edit.isEnabled = checked
            }

            update_interval_edit.filters = arrayOf(IntFilter(1..3600))
            background_update_interval_edit.filters = arrayOf(IntFilter(0..10800))
            timeout_edit.filters = arrayOf(IntFilter(5..60))

            if (savedInstanceState == null) {
                val server = activity.server
                if (server == null) {
                    port_edit.setText("9091")
                    api_path_edit.setText("/transmission/rpc")
                    update_interval_edit.setText("5")
                    background_update_interval_edit.setText("60")
                    timeout_edit.setText("30")
                } else {
                    name_edit.setText(server.name)
                    address_edit.setText(server.address)
                    port_edit.setText(server.port.toString())
                    api_path_edit.setText(server.apiPath)
                    https_check_box.isChecked = server.httpsEnabled
                    authentication_check_box.isChecked = server.authentication
                    username_edit.setText(server.username)
                    password_edit.setText(server.password)
                    update_interval_edit.setText(server.updateInterval.toString())
                    background_update_interval_edit.setText(server.backgroundUpdateInterval.toString())
                    timeout_edit.setText(server.timeout.toString())
                }
            }
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
            val backgroundUpdateIntervalOk = checkLength(background_update_interval_edit)
            val timeoutOk = checkLength(timeout_edit)

            val nameEditText = name_edit.text.toString()

            if (nameOk &&
                    addressOk &&
                    portOk &&
                    apiPathOk &&
                    updateIntervalOk &&
                    backgroundUpdateIntervalOk &&
                    timeoutOk) {
                if (nameEditText != activity.server?.name &&
                        Servers.servers.find { it.name == nameEditText } != null) {
                    OverwriteDialogFragment().show(fragmentManager, null)
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
                backgroundUpdateInterval = background_update_interval_edit.text.toString().toInt()
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

    class CertificatesFragment : Fragment() {
        private val activity: ServerEditActivity
            get() {
                return getActivity() as ServerEditActivity
            }

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            activity.title = getString(R.string.certificates)
            return inflater.inflate(R.layout.server_edit_activity_certificates_fragment,
                                    container,
                                    false)
        }

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

        override fun onDestroyView() {
            super.onDestroyView()

            val newServer = activity.newServer
            newServer.selfSignedCertificateEnabled = self_signed_certificate_check_box.isChecked
            newServer.selfSignedCertificate = self_signed_certificate_edit.text.toString()
            newServer.clientCertificateEnabled = client_certificate_check_box.isChecked
            newServer.clientCertificate = client_certificate_edit.text.toString()
        }
    }
}