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

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.text.trimmedLength
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.rpc.Server
import org.equeim.tremotesf.databinding.ServerEditCertificatesFragmentBinding
import org.equeim.tremotesf.databinding.ServerEditFragmentBinding
import org.equeim.tremotesf.databinding.ServerEditProxyFragmentBinding
import org.equeim.tremotesf.rpc.GlobalRpc
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.navController
import org.equeim.tremotesf.ui.utils.*
import timber.log.Timber


class ServerEditFragment : NavigationFragment(R.layout.server_edit_fragment, 0) {
    private lateinit var model: ServerEditFragmentViewModel

    private var requestLocationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val binding by viewBinding(ServerEditFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            model = ServerEditFragmentViewModel.get(this@ServerEditFragment)
            requestLocationPermissionLauncher =
                model.locationPermissionHelper?.registerWithFragment(this@ServerEditFragment)
            requestBackgroundLocationPermissionLauncher =
                model.backgroundLocationPermissionHelper?.registerWithFragment(this@ServerEditFragment)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            portEdit.filters = arrayOf(IntFilter(Server.portRange))

            proxySettingsButton.setOnClickListener {
                navigate(ServerEditFragmentDirections.toProxySettingsFragment())
            }

            certificatedButton.setOnClickListener {
                navigate(ServerEditFragmentDirections.toCertificatesFragment())
            }
            httpsCheckBox.setDependentViews(certificatedButton)

            authenticationCheckBox.setDependentViews(usernameEditLayout, passwordEditLayout)

            updateIntervalEdit.filters = arrayOf(IntFilter(Server.updateIntervalRange))
            timeoutEdit.filters = arrayOf(IntFilter(Server.timeoutRange))

            wifiAutoConnectCheckbox.setOnClickListener {
                if (wifiAutoConnectCheckbox.isChecked) {
                    model.locationPermissionHelper?.requestPermission(
                        this@ServerEditFragment,
                        checkNotNull(requestLocationPermissionLauncher)
                    )
                    model.backgroundLocationPermissionHelper?.checkPermission(requireContext())
                }
            }
            wifiAutoConnectCheckbox.setDependentViews(
                locationErrorButton,
                wifiAutoConnectSsidEditLayout,
                setSsidFromCurrentNetworkButton,
                backgroundWifiNetworksExplanation,
                backgroundLocationPermissionButton
            )
            model.locationPermissionHelper?.let { locationPermissionHelper ->
                combine(locationPermissionHelper.permissionGranted, model.locationEnabled, ::Pair)
                    .onEach { (locationPermissionGranted, locationEnabled) ->
                        locationErrorButton.apply {
                            when {
                                !locationPermissionGranted -> {
                                    isVisible = true
                                    setText(R.string.request_location_permission)
                                    setOnClickListener {
                                        locationPermissionHelper.requestPermission(
                                            this@ServerEditFragment,
                                            checkNotNull(requestLocationPermissionLauncher)
                                        )
                                    }
                                }
                                !locationEnabled -> {
                                    isVisible = true
                                    setText(R.string.enable_location)
                                    setOnClickListener {
                                        navigate(ServerEditFragmentDirections.toEnableLocationDialog())
                                    }
                                }
                                else -> isVisible = false
                            }
                        }
                    }.launchAndCollectWhenStarted(viewLifecycleOwner)
            }

            val backgroundLocationPermissionHelper = model.backgroundLocationPermissionHelper
            if (backgroundLocationPermissionHelper != null) {
                backgroundWifiNetworksExplanation.apply {
                    setText(R.string.background_wifi_networks_explanation_fdroid)
                    isVisible = true
                }
                backgroundLocationPermissionButton.isVisible = true

                backgroundLocationPermissionHelper.permissionGranted.onEach { granted ->
                    Timber.i("background granted = $granted")
                    backgroundLocationPermissionButton.apply {
                        if (granted) {
                            setIconResource(R.drawable.ic_done_24dp)
                            setText(R.string.background_location_permission_granted)
                        } else {
                            icon = null
                            setText(R.string.request_background_location_permission)
                        }
                        isClickable = !granted
                    }
                }.launchAndCollectWhenStarted(viewLifecycleOwner)
                backgroundLocationPermissionButton.setOnClickListener {
                    backgroundLocationPermissionHelper.requestPermission(
                        this@ServerEditFragment,
                        checkNotNull(requestBackgroundLocationPermissionLauncher)
                    )
                }
            } else if (ServerEditFragmentViewModel.canRequestBackgroundLocationPermission()) {
                backgroundWifiNetworksExplanation.apply {
                    setText(R.string.background_wifi_networks_explanation_google)
                    isVisible = true
                }
            }

            setSsidFromCurrentNetworkButton.setOnClickListener {
                val ssid = GlobalRpc.wifiNetworkController.currentWifiSsid

                if (ssid != null) {
                    wifiAutoConnectSsidEdit.setText(ssid)
                } else {
                    Toast.makeText(requireContext(), R.string.current_ssid_error, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        model.locationPermissionHelper?.run {
            permissionRequestResult
                .filter { it }
                .onEach {
                    if (!model.locationEnabled.value) {
                        navigate(ServerEditFragmentDirections.toEnableLocationDialog())
                    }
                }.launchAndCollectWhenStarted(viewLifecycleOwner)
        }

        toolbar?.setTitle(if (model.existingServer == null) R.string.add_server else R.string.edit_server)

        binding.saveButton.apply {
            setText(if (model.existingServer == null) {
                R.string.add
            } else {
                R.string.save
            })
            isExtended = true
            setOnClickListener { onDone() }
        }

        if (!model.populatedUiFromServer) {
            with(binding) {
                val server = model.server
                nameEdit.setText(server.name)
                addressEdit.setText(server.address)
                portEdit.setText(server.port.toString())
                apiPathEdit.setText(server.apiPath)
                httpsCheckBox.isChecked = server.httpsEnabled
                authenticationCheckBox.isChecked = server.authentication
                usernameEdit.setText(server.username)
                passwordEdit.setText(server.password)
                updateIntervalEdit.setText(server.updateInterval.toString())
                timeoutEdit.setText(server.timeout.toString())
                wifiAutoConnectCheckbox.isChecked = server.autoConnectOnWifiNetworkEnabled
                wifiAutoConnectSsidEdit.setText(server.autoConnectOnWifiNetworkSSID)
            }
            model.populatedUiFromServer = true
        }
    }

    override fun onStart() {
        Timber.i("onStart() called")
        super.onStart()
        with(model) {
            locationPermissionHelper?.checkPermission(requireContext())
            backgroundLocationPermissionHelper?.checkPermission(requireContext())
            checkIfLocationEnabled()
        }
    }

    private fun onDone(): Boolean {
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

        with(binding) {
            val nameOk = checkLength(nameEdit)
            val addressOk = checkLength(addressEdit)
            val portOk = checkLength(portEdit)
            val apiPathOk = checkLength(apiPathEdit)
            val updateIntervalOk = checkLength(updateIntervalEdit)
            val timeoutOk = checkLength(timeoutEdit)

            val nameEditText = nameEdit.text?.toString() ?: ""

            if (nameOk &&
                addressOk &&
                portOk &&
                apiPathOk &&
                updateIntervalOk &&
                timeoutOk
            ) {
                if (nameEditText != model.existingServer?.name &&
                    GlobalServers.servers.value.find { it.name == nameEditText } != null
                ) {
                    navigate(ServerEditFragmentDirections.toOverwriteDialog())
                } else {
                    save()
                }
            }
        }

        return true
    }

    fun save() {
        with(binding) {
            model.server.apply {
                name = nameEdit.text?.toString()?.trim() ?: ""
                address = addressEdit.text?.toString()?.trim() ?: ""
                port = portEdit.text?.toString()?.toIntOrNull() ?: 0
                apiPath = apiPathEdit.text?.toString()?.trim() ?: ""
                httpsEnabled = httpsCheckBox.isChecked
                authentication = authenticationCheckBox.isChecked
                username = usernameEdit.text?.toString()?.trim() ?: ""
                password = passwordEdit.text?.toString()?.trim() ?: ""
                updateInterval = updateIntervalEdit.text?.toString()?.toIntOrNull() ?: 0
                timeout = timeoutEdit.text?.toString()?.toIntOrNull() ?: 0
                autoConnectOnWifiNetworkEnabled = wifiAutoConnectCheckbox.isChecked
                autoConnectOnWifiNetworkSSID =
                    wifiAutoConnectSsidEdit.text?.toString()?.trim() ?: ""
            }
        }

        model.existingServer.let { existing ->
            if (existing == null) {
                GlobalServers.addServer(model.server)
            } else {
                GlobalServers.setServer(existing, model.server)
            }
        }

        navController.popBackStack(R.id.server_edit_fragment, true)
    }
}

class ServerEditFragmentViewModel(args: ServerEditFragmentArgs, application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    companion object {
        suspend fun get(fragment: Fragment): ServerEditFragmentViewModel {
            val entry = fragment.navController.getBackStackEntry(R.id.server_edit_fragment)
            return entry.withCreated {
                ViewModelProvider(
                    entry,
                    fragment.navArgsViewModelFactory(ServerEditFragmentArgs::fromBundle, ::ServerEditFragmentViewModel)
                )[ServerEditFragmentViewModel::class.java]
            }
        }

        private fun requiredLocationPermission(): String? {
            val sdk = Build.VERSION.SDK_INT
            return when {
                sdk >= Build.VERSION_CODES.Q -> Manifest.permission.ACCESS_FINE_LOCATION
                sdk >= Build.VERSION_CODES.O -> Manifest.permission.ACCESS_COARSE_LOCATION
                else -> null
            }
        }

        private fun requestLocationPermissions(): List<String> {
            val sdk = Build.VERSION.SDK_INT
            return when {
                sdk >= Build.VERSION_CODES.S -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                else -> requiredLocationPermission()?.let { listOf(it) } ?: emptyList()
            }
        }

        private fun locationNeedsToBeEnabled() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        fun canRequestBackgroundLocationPermission() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        private fun allowedToRequestBackgroundLocationPermission() = !BuildConfig.GOOGLE
    }

    private val serverName: String? = args.server

    val existingServer =
        if (serverName != null) GlobalServers.servers.value.find { it.name == serverName } else null
    val server by savedState(savedStateHandle) { existingServer?.copy() ?: Server() }

    var populatedUiFromServer by savedState(savedStateHandle, false)

    val locationPermissionHelper = requiredLocationPermission()?.let { permission ->
        RuntimePermissionHelper(
            permission,
            R.string.location_permission_rationale,
            requestLocationPermissions()
        )
    }

    val backgroundLocationPermissionHelper = if (canRequestBackgroundLocationPermission() && allowedToRequestBackgroundLocationPermission()) {
        RuntimePermissionHelper(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            R.string.background_location_permission_rationale
        )
    } else {
        null
    }

    private val _locationEnabled = MutableStateFlow(isLocationEnabled())
    val locationEnabled: StateFlow<Boolean> by ::_locationEnabled

    private fun isLocationEnabled(): Boolean {
        if (!locationNeedsToBeEnabled()) return true

        val locationManager = getApplication<Application>().getSystemService<LocationManager>()
        if (locationManager == null) {
            Timber.e("isLocationEnabled: LocationManager is null")
            return false
        }
        if (LocationManagerCompat.isLocationEnabled(locationManager)) {
            Timber.i("isLocationEnabled: location is enabled")
            return true
        }
        Timber.i("isLocationEnabled: location is disabled")
        return false
    }

    fun checkIfLocationEnabled() {
        _locationEnabled.value = isLocationEnabled()
    }
}

class EnableLocationDialog : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.request_enable_location)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.go_to_settings) { _, _ -> goToLocationSettings() }
            .create()
    }

    private fun goToLocationSettings() {
        Timber.i("Going to system location settings activity")
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
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

class ServerCertificatesFragment : NavigationFragment(
    R.layout.server_edit_certificates_fragment,
    R.string.certificates
) {
    private lateinit var getServerCertificateLauncher: ActivityResultLauncher<Unit>
    private lateinit var getClientCertificateLauncher: ActivityResultLauncher<Unit>

    private lateinit var mainModel: ServerEditFragmentViewModel

    private val binding by viewBinding(ServerEditCertificatesFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handleCertificateResult = { uri: Uri, view: TextView ->
            Timber.d("handleCertificateResult() called with: uri = $uri, view = $view")
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val stream = requireContext().contentResolver.openInputStream(uri)
                    if (stream != null) {
                        val text = stream.reader().readText()
                        withContext(Dispatchers.Main) {
                            view.text = text
                        }
                    } else {
                        Timber.e("handleCertificateResult: InputStream is null")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "handleCertificateResult: failed to read content")
                }
            }
        }

        getServerCertificateLauncher = registerForActivityResult(GetPemFileContract()) {
            if (it != null) handleCertificateResult(it, binding.selfSignedCertificateEdit)
        }
        getClientCertificateLauncher = registerForActivityResult(GetPemFileContract()) {
            if (it != null) handleCertificateResult(it, binding.clientCertificateEdit)
        }

        lifecycleScope.launch {
            mainModel = ServerEditFragmentViewModel.get(this@ServerCertificatesFragment)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            selfSignedCertificateCheckBox.setDependentViews(selfSignedCertificateLayout, selfSignedCertificateLoadFromFile)
            clientCertificateCheckBox.setDependentViews(clientCertificateLayout, clientCertificateLoadFromFile)

            selfSignedCertificateLoadFromFile.setOnClickListener {
                try {
                    getServerCertificateLauncher.launch()
                } catch (e: ActivityNotFoundException) {
                    Timber.e(e, "Failed to start activity")
                }
            }
            clientCertificateLoadFromFile.setOnClickListener {
                try {
                    getClientCertificateLauncher.launch()
                } catch (e: ActivityNotFoundException) {
                    Timber.e(e, "Failed to start activity")
                }
            }

            val model = ViewModelProvider(this@ServerCertificatesFragment)[ServerCertificatesFragmentModel::class.java]
            if (!model.populatedUiFromServer) {
                with(mainModel.server) {
                    selfSignedCertificateCheckBox.isChecked = selfSignedCertificateEnabled
                    selfSignedCertificateEdit.setText(selfSignedCertificate)
                    clientCertificateCheckBox.isChecked = clientCertificateEnabled
                    clientCertificateEdit.setText(clientCertificate)
                }
                model.populatedUiFromServer = true
            }
        }
    }

    override fun onNavigatedFrom() {
        if (view != null) {
            with(binding) {
                mainModel.server.apply {
                    selfSignedCertificateEnabled = selfSignedCertificateCheckBox.isChecked
                    selfSignedCertificate = selfSignedCertificateEdit.text?.toString() ?: ""
                    clientCertificateEnabled = clientCertificateCheckBox.isChecked
                    clientCertificate = clientCertificateEdit.text?.toString() ?: ""
                }
            }
        }
    }

    private class GetPemFileContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-pem-file", "text/plain"))
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
        }
    }
}

class ServerCertificatesFragmentModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    var populatedUiFromServer by savedState(savedStateHandle, false)
}

class ServerProxySettingsFragment : NavigationFragment(
    R.layout.server_edit_proxy_fragment,
    R.string.proxy_settings
) {
    private companion object {
        // Should match R.array.proxy_type_items
        val proxyTypeItems = arrayOf(
            org.equeim.libtremotesf.Server.ProxyType.Default,
            org.equeim.libtremotesf.Server.ProxyType.Http,
            org.equeim.libtremotesf.Server.ProxyType.Socks5
        )
    }

    private lateinit var mainModel: ServerEditFragmentViewModel
    private lateinit var proxyTypeItemValues: Array<String>

    private val binding by viewBinding(ServerEditProxyFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proxyTypeItemValues = resources.getStringArray(R.array.proxy_type_items)
        lifecycleScope.launch {
            mainModel = ServerEditFragmentViewModel.get(this@ServerProxySettingsFragment)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            proxyTypeView.setAdapter(ArrayDropdownAdapter(proxyTypeItemValues))
            proxyTypeView.setOnItemClickListener { _, _, position, _ ->
                setEditable(proxyTypeItems[position] != org.equeim.libtremotesf.Server.ProxyType.Default)
            }

            portEdit.filters = arrayOf(IntFilter(Server.portRange))

            val model = ViewModelProvider(this@ServerProxySettingsFragment)[ServerProxySettingsFragmentModel::class.java]
            if (!model.populatedUiFromServer) {
                with(mainModel.server) {
                    proxyTypeView.setText(proxyTypeItemValues[proxyTypeItems.indexOf(nativeProxyType())])
                    addressEdit.setText(proxyHostname)
                    portEdit.setText(proxyPort.toString())
                    usernameEdit.setText(proxyUser)
                    passwordEdit.setText(proxyPassword)

                    if (nativeProxyType() == org.equeim.libtremotesf.Server.ProxyType.Default) {
                        setEditable(false)
                    }
                }
                model.populatedUiFromServer = true
            }
        }
    }

    override fun onNavigatedFrom() {
        if (view != null) {
            with(binding) {
                mainModel.server.apply {
                    proxyType = Server.fromNativeProxyType(
                        proxyTypeItems[proxyTypeItemValues.indexOf(proxyTypeView.text.toString())]
                    )
                    proxyHostname = addressEdit.text?.toString() ?: ""
                    proxyPort = portEdit.text?.toString()?.toIntOrNull() ?: 0
                    proxyUser = usernameEdit.text?.toString() ?: ""
                    proxyPassword = passwordEdit.text?.toString() ?: ""
                }
            }
        }
    }

    private fun setEditable(editable: Boolean) {
        with(binding) {
            addressEditLayout.isEnabled = editable
            portEditLayout.isEnabled = editable
            usernameEditLayout.isEnabled = editable
            passwordEditLayout.isEnabled = editable
        }
    }
}

class ServerProxySettingsFragmentModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    var populatedUiFromServer by savedState(savedStateHandle, false)
}
