/*
 * Copyright (C) 2017 Alexey Rochev <equeim@gmail.com>
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
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentTransaction

import android.os.Bundle

import android.widget.CheckBox
import android.widget.EditText

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import android.support.v7.app.AlertDialog

import org.equeim.tremotesf.BaseActivity
import org.equeim.tremotesf.R
import org.equeim.tremotesf.Server
import org.equeim.tremotesf.Servers
import org.equeim.tremotesf.Settings
import org.equeim.tremotesf.utils.IntFilter
import org.equeim.tremotesf.utils.setChildrenEnabled


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
        server = Servers.servers.find { server -> server.name == serverName }
        if (server != null) {
            server!!.copyTo(newServer)
        }

        if (savedInstanceState == null) {
            fragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, MainFragment(), MainFragment.TAG)
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
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

        private var nameEdit: EditText? = null
        private var addressEdit: EditText? = null
        private var portEdit: EditText? = null
        private var apiPathEdit: EditText? = null
        private var httpsCheckBox: CheckBox? = null
        private var authenticationCheckBox: CheckBox? = null
        private var usernameEdit: EditText? = null
        private var passwordEdit: EditText? = null
        private var updateIntervalEdit: EditText? = null
        private var backgroundUpdateIntervalEdit: EditText? = null
        private var timeoutEdit: EditText? = null

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

            val view = inflater.inflate(R.layout.server_edit_activity, container, false)

            nameEdit = view.findViewById(R.id.name_edit) as EditText
            addressEdit = view.findViewById(R.id.address_edit) as EditText

            portEdit = view.findViewById(R.id.port_edit) as EditText
            portEdit!!.filters = arrayOf(IntFilter(0..65535))

            apiPathEdit = view.findViewById(R.id.api_path_edit) as EditText

            httpsCheckBox = view.findViewById(R.id.https_check_box) as CheckBox
            httpsCheckBox!!.isChecked = false

            val certificatesItem = view.findViewById(R.id.certificates_item) as ViewGroup
            certificatesItem.isEnabled = false
            certificatesItem.setChildrenEnabled(false)
            certificatesItem.setOnClickListener {
                fragmentManager
                        .beginTransaction()
                        .replace(android.R.id.content, CertificatesFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .addToBackStack(null)
                        .commit()
            }
            httpsCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
                certificatesItem.isEnabled = checked
                certificatesItem.setChildrenEnabled(checked)
            }

            authenticationCheckBox = view.findViewById(R.id.authentication_check_box) as CheckBox
            authenticationCheckBox!!.isChecked = false

            usernameEdit = view.findViewById(R.id.username_edit) as EditText
            usernameEdit!!.isEnabled = false
            passwordEdit = view.findViewById(R.id.password_edit) as EditText
            passwordEdit!!.isEnabled = false
            authenticationCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
                usernameEdit!!.isEnabled = checked
                passwordEdit!!.isEnabled = checked
            }

            updateIntervalEdit = view.findViewById(R.id.update_interval_edit) as EditText
            updateIntervalEdit!!.filters = arrayOf(IntFilter(1..3600))

            backgroundUpdateIntervalEdit = view.findViewById(R.id.background_update_interval_edit) as EditText
            backgroundUpdateIntervalEdit!!.filters = arrayOf(IntFilter(0..10800))

            timeoutEdit = view.findViewById(R.id.timeout_edit) as EditText
            timeoutEdit!!.filters = arrayOf(IntFilter(5..60))

            if (savedInstanceState == null) {
                val server = activity.server
                if (server == null) {
                    portEdit!!.setText("9091")
                    apiPathEdit!!.setText("/transmission/rpc")
                    updateIntervalEdit!!.setText("5")
                    backgroundUpdateIntervalEdit!!.setText("60")
                    timeoutEdit!!.setText("30")
                } else {
                    nameEdit!!.setText(server.name)
                    addressEdit!!.setText(server.address)
                    portEdit!!.setText(server.port.toString())
                    apiPathEdit!!.setText(server.apiPath)
                    httpsCheckBox!!.isChecked = server.httpsEnabled
                    authenticationCheckBox!!.isChecked = server.authentication
                    usernameEdit!!.setText(server.username)
                    passwordEdit!!.setText(server.password)
                    updateIntervalEdit!!.setText(server.updateInterval.toString())
                    backgroundUpdateIntervalEdit!!.setText(server.backgroundUpdateInterval.toString())
                    timeoutEdit!!.setText(server.timeout.toString())
                }
            }

            return view
        }

        override fun onDestroyView() {
            super.onDestroyView()
            nameEdit = null
            addressEdit = null
            portEdit = null
            apiPathEdit = null
            httpsCheckBox = null
            authenticationCheckBox = null
            usernameEdit = null
            passwordEdit = null
            updateIntervalEdit = null
            backgroundUpdateIntervalEdit = null
            timeoutEdit = null
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
                if (edit.text.trim().isEmpty()) {
                    edit.error = error
                    return false
                }
                return true
            }

            val nameOk = checkLength(nameEdit!!)
            val addressOk = checkLength(addressEdit!!)
            val portOk = checkLength(portEdit!!)
            val apiPathOk = checkLength(apiPathEdit!!)
            val updateIntervalOk = checkLength(updateIntervalEdit!!)
            val backgroundUpdateIntervalOk = checkLength(backgroundUpdateIntervalEdit!!)
            val timeoutOk = checkLength(timeoutEdit!!)

            if (nameOk &&
                addressOk &&
                portOk &&
                apiPathOk &&
                updateIntervalOk &&
                backgroundUpdateIntervalOk &&
                timeoutOk) {
                if ((nameEdit!!.text.toString() != activity.server?.name) &&
                    Servers.servers.find { server -> (server.name == nameEdit!!.text.toString()) } != null) {
                    OverwriteDialogFragment().show(fragmentManager, null)
                } else {
                    save()
                }
            }

            return true
        }

        private fun save() {
            val newServer = activity.newServer
            newServer.name = nameEdit!!.text.toString()
            newServer.address = addressEdit!!.text.toString()
            newServer.port = portEdit!!.text.toString().toInt()
            newServer.apiPath = apiPathEdit!!.text.toString()
            newServer.httpsEnabled = httpsCheckBox!!.isChecked
            newServer.authentication = authenticationCheckBox!!.isChecked
            newServer.username = usernameEdit!!.text.toString()
            newServer.password = passwordEdit!!.text.toString()
            newServer.updateInterval = updateIntervalEdit!!.text.toString().toInt()
            newServer.backgroundUpdateInterval = backgroundUpdateIntervalEdit!!.text.toString().toInt()
            newServer.timeout = timeoutEdit!!.text.toString().toInt()

            if (activity.server == null) {
                Servers.addServer(newServer)
            } else {
                Servers.setServer(activity.server!!, newServer)
            }

            activity.finish()
        }

        class OverwriteDialogFragment : DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return AlertDialog.Builder(activity)
                        .setMessage(R.string.server_exists)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.overwrite, { dialog, which ->
                            (activity.fragmentManager.findFragmentByTag(MainFragment.TAG) as MainFragment).save()
                        }).create()
            }
        }
    }

    class CertificatesFragment : Fragment() {
        private val activity: ServerEditActivity
            get() {
                return getActivity() as ServerEditActivity
            }

        private var selfSignedCertificateCheckBox: CheckBox? = null
        private var selfSignedCertificateEdit: EditText? = null
        private var clientCertificateCheckBox: CheckBox? = null
        private var clientCertificateEdit: EditText? = null

        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            activity.title = getString(R.string.certificates)

            val view = inflater.inflate(R.layout.server_edit_activity_certificates,
                                        container,
                                        false)

            selfSignedCertificateCheckBox = view.findViewById(R.id.self_signed_certificate_check_box) as CheckBox
            selfSignedCertificateCheckBox!!.isChecked = false

            val selfSignedCertificateLayout = view.findViewById(R.id.self_signed_certificate_layout)
            selfSignedCertificateLayout.isEnabled = false
            selfSignedCertificateCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
                selfSignedCertificateLayout.isEnabled = checked
            }

            selfSignedCertificateEdit = view.findViewById(R.id.self_signed_certificate_edit) as EditText

            clientCertificateCheckBox = view.findViewById(R.id.client_certificate_check_box) as CheckBox
            clientCertificateCheckBox!!.isChecked = false

            val clientCertificateLayout = view.findViewById(R.id.client_certificate_layout)
            clientCertificateLayout.isEnabled = false
            clientCertificateCheckBox!!.setOnCheckedChangeListener { checkBox, checked ->
                clientCertificateLayout.isEnabled = checked
            }

            clientCertificateEdit = view.findViewById(R.id.client_certificate_edit) as EditText

            if (savedInstanceState == null) {
                val newServer = activity.newServer
                selfSignedCertificateCheckBox!!.isChecked = newServer.selfSignedSertificateEnabled
                selfSignedCertificateEdit!!.setText(newServer.selfSignedSertificate)
                clientCertificateCheckBox!!.isChecked = newServer.clientCertificateEnabled
                clientCertificateEdit!!.setText(newServer.clientCertificate)
            }

            return view
        }

        override fun onDestroyView() {
            super.onDestroyView()

            val newServer = activity.newServer
            newServer.selfSignedSertificateEnabled = selfSignedCertificateCheckBox!!.isChecked
            newServer.selfSignedSertificate = selfSignedCertificateEdit!!.text.toString()
            newServer.clientCertificateEnabled = clientCertificateCheckBox!!.isChecked
            newServer.clientCertificate = clientCertificateEdit!!.text.toString()

            selfSignedCertificateCheckBox = null
            selfSignedCertificateEdit = null
            clientCertificateCheckBox = null
            clientCertificateEdit = null
        }
    }
}