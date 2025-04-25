// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipDescription
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.databinding.ServerEditCertificatesFragmentBinding
import org.equeim.tremotesf.databinding.ServerEditFragmentBinding
import org.equeim.tremotesf.databinding.ServerEditProxyFragmentBinding
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.ui.utils.ArrayDropdownAdapter
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.doAfterTextChangedAndNotEmpty
import org.equeim.tremotesf.ui.utils.extendWhenImeIsHidden
import org.equeim.tremotesf.ui.utils.handleNumberRangeError
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.savedState
import org.equeim.tremotesf.ui.utils.setDependentViews
import org.equeim.tremotesf.ui.utils.textInputLayout
import org.equeim.tremotesf.ui.utils.viewLifecycleObject
import timber.log.Timber
import java.io.FileNotFoundException
import java.net.Proxy
import kotlin.time.Duration.Companion.seconds


class ServerEditFragment : NavigationFragment(R.layout.server_edit_fragment, 0) {
    private lateinit var model: ServerEditFragmentViewModel

    private lateinit var requestLocationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private var requestBackgroundLocationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val binding by viewLifecycleObject(ServerEditFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ServerEditFragmentViewModel.get(navController)
        requestLocationPermissionLauncher =
            model.locationPermissionHelper.registerWithFragment(this@ServerEditFragment)
        requestBackgroundLocationPermissionLauncher =
            model.backgroundLocationPermissionHelper?.registerWithFragment(this@ServerEditFragment)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            nameEdit.doAfterTextChangedAndNotEmpty { nameEdit.textInputLayout.error = null }
            addressEdit.doAfterTextChangedAndNotEmpty { addressEdit.textInputLayout.error = null }

            portEdit.handleNumberRangeError(Server.portRange)

            apiPathEdit.doAfterTextChangedAndNotEmpty { apiPathEdit.textInputLayout.error = null }

            httpsHint.isVisible = httpsCheckBox.isChecked
            httpsCheckBox.setOnCheckedChangeListener { _, isChecked ->
                httpsHint.isVisible = isChecked
            }

            proxySettingsButton.setOnClickListener {
                navigate(ServerEditFragmentDirections.toProxySettingsFragment())
            }

            certificatedButton.setOnClickListener {
                navigate(ServerEditFragmentDirections.toCertificatesFragment())
            }

            authenticationCheckBox.setDependentViews(usernameEditLayout, passwordEditLayout)

            updateIntervalEdit.handleNumberRangeError(Server.updateIntervalRangeInSeconds)
            timeoutEdit.handleNumberRangeError(Server.timeoutRangeInSeconds)

            wifiAutoConnectCheckbox.setOnClickListener {
                if (wifiAutoConnectCheckbox.isChecked) {
                    model.locationPermissionHelper.requestPermission(
                        this@ServerEditFragment,
                        requestLocationPermissionLauncher
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
            combine(model.locationPermissionHelper.permissionGranted, model.locationEnabled, ::Pair)
                .onEach { (locationPermissionGranted, locationEnabled) ->
                    locationErrorButton.apply {
                        when {
                            !locationPermissionGranted -> {
                                isVisible = true
                                setText(R.string.request_location_permission)
                                setOnClickListener {
                                    model.locationPermissionHelper.requestPermission(
                                        this@ServerEditFragment,
                                        requestLocationPermissionLauncher
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
                viewLifecycleOwner.lifecycleScope.launch {
                    val ssid = GlobalServers.wifiNetworkController.getCurrentWifiSsid()
                    if (ssid != null) {
                        wifiAutoConnectSsidEdit.setText(ssid)
                    } else {
                        Toast.makeText(requireContext(), R.string.current_ssid_error, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }

        model.locationPermissionHelper.permissionRequestResult
            .receiveAsFlow()
            .filter { it }
            .onEach {
                if (!model.locationEnabled.value) {
                    navigate(ServerEditFragmentDirections.toEnableLocationDialog())
                }
            }.launchAndCollectWhenStarted(viewLifecycleOwner)

        toolbar.setTitle(if (model.editingServer == null) R.string.add_server else R.string.edit_server)

        binding.saveButton.apply {
            setText(
                if (model.editingServer == null) {
                    R.string.add
                } else {
                    R.string.save
                }
            )
            setOnClickListener { onDone() }
        }
        binding.saveButton.extendWhenImeIsHidden(requiredActivity.windowInsets, viewLifecycleOwner)

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
                updateIntervalEdit.setText(server.updateInterval.inWholeSeconds.toString())
                timeoutEdit.setText(server.timeout.inWholeSeconds.toString())
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
            locationPermissionHelper.checkPermission(requireContext())
            backgroundLocationPermissionHelper?.checkPermission(requireContext())
            checkIfLocationEnabled()
        }
    }

    private fun onDone() {
        with(binding) {
            val emptyFieldError = getText(R.string.empty_field_error)
            nameEdit.checkLength(emptyFieldError)
            addressEdit.checkLength(emptyFieldError)
            portEdit.checkLength(emptyFieldError)
            apiPathEdit.checkLength(emptyFieldError)
            updateIntervalEdit.checkLength(emptyFieldError)
            timeoutEdit.checkLength(emptyFieldError)

            if (nameEdit.dontHaveError &&
                addressEdit.dontHaveError &&
                portEdit.dontHaveError &&
                apiPathEdit.dontHaveError &&
                updateIntervalEdit.dontHaveError &&
                timeoutEdit.dontHaveError
            ) {
                val nameEditText = nameEdit.text?.toString() ?: ""
                val editingServer = model.editingServer
                if ((editingServer == null || nameEditText != editingServer.name) &&
                    GlobalServers.serversState.value.servers.find { it.name == nameEditText } != null
                ) {
                    navigate(ServerEditFragmentDirections.toOverwriteDialog())
                } else {
                    save()
                }
            }
        }
    }

    fun save() {
        with(binding) {
            model.server = model.server.copy(
                name = nameEdit.text?.toString()?.trim() ?: "",
                address = addressEdit.text?.toString()?.trim() ?: "",
                port = portEdit.text?.toString()?.toIntOrNull() ?: 0,
                apiPath = apiPathEdit.text?.toString()?.trim() ?: "",
                httpsEnabled = httpsCheckBox.isChecked,
                authentication = authenticationCheckBox.isChecked,
                username = usernameEdit.text?.toString()?.trim() ?: "",
                password = passwordEdit.text?.toString()?.trim() ?: "",
                updateInterval = updateIntervalEdit.text?.toString()?.toIntOrNull()?.seconds
                    ?: Server.DEFAULT_UPDATE_INTERVAL,
                timeout = timeoutEdit.text?.toString()?.toIntOrNull()?.seconds
                    ?: Server.DEFAULT_TIMEOUT,
                autoConnectOnWifiNetworkEnabled = wifiAutoConnectCheckbox.isChecked,
                autoConnectOnWifiNetworkSSID =
                wifiAutoConnectSsidEdit.text?.toString()?.trim() ?: ""
            )
        }
        if (model.editingServer == null || model.server != model.editingServer) {
            GlobalServers.addOrReplaceServer(model.server, previousName = model.editingServer?.name)
        } else {
            Timber.d("save: server did not change")
        }
        navController.popBackStack(R.id.server_edit_fragment, true)
    }
}

class ServerEditFragmentViewModel(
    args: ServerEditFragmentArgs,
    application: Application,
    savedStateHandle: SavedStateHandle,
) :
    AndroidViewModel(application) {
    companion object {
        fun get(navController: NavController): ServerEditFragmentViewModel {
            val entry = navController.getBackStackEntry(R.id.server_edit_fragment)
            val factory = viewModelFactory {
                initializer {
                    val args = ServerEditFragmentArgs.fromBundle(checkNotNull(entry.arguments))
                    ServerEditFragmentViewModel(
                        args,
                        checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                        createSavedStateHandle()
                    )
                }
            }
            return ViewModelProvider(entry, factory)[ServerEditFragmentViewModel::class.java]
        }

        private fun requiredLocationPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.ACCESS_COARSE_LOCATION
        }

        private fun requestLocationPermissions(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                listOf(requiredLocationPermission())
            }
        }

        private fun locationNeedsToBeEnabled() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        fun canRequestBackgroundLocationPermission() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private val serverName: String? = args.server

    val editingServer: Server? =
        if (serverName != null) GlobalServers.serversState.value.servers.find { it.name == serverName } else null
    var server: Server by savedState(savedStateHandle) { editingServer?.copy() ?: Server() }

    var populatedUiFromServer by savedState(savedStateHandle, false)

    val locationPermissionHelper = RuntimePermissionHelper(
        requiredPermission = requiredLocationPermission(),
        permissionRationaleStringId = R.string.location_permission_rationale,
        requestPermissions = requestLocationPermissions()
    )

    val backgroundLocationPermissionHelper =
        if (canRequestBackgroundLocationPermission() && !BuildConfig.GOOGLE) {
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

    private val binding by viewLifecycleObject(ServerEditCertificatesFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getServerCertificateLauncher = registerForActivityResult(GetPemFileContract()) {
            if (it != null) handleCertificateResult(it, binding.selfSignedCertificateEdit)
        }
        getClientCertificateLauncher = registerForActivityResult(GetPemFileContract()) {
            if (it != null) handleCertificateResult(it, binding.clientCertificateEdit)
        }
        mainModel = ServerEditFragmentViewModel.get(navController)
    }

    private fun handleCertificateResult(uri: Uri, view: TextView) {
        Timber.d("handleCertificateResult() called with: uri = $uri, view = $view")
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val certificate = readCertificate(uri)
            if (certificate != null) {
                withContext(Dispatchers.Main) {
                    view.text = certificate
                }
            }
        }
    }

    private fun readCertificate(uri: Uri): String? {
        return try {
            val stream = requireContext().contentResolver.openInputStream(uri)
            if (stream != null) {
                stream.use { it.reader().readText() }
            } else {
                Timber.e("readCertificate: failed to read certificate, ContentResolver returned null InputStream")
                null
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "readCertificate: failed to read certificate")
            null
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            selfSignedCertificateCheckBox.setDependentViews(
                selfSignedCertificateLayout,
                selfSignedCertificateLoadFromFile
            )
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
                mainModel.server = mainModel.server.copy(
                    selfSignedCertificateEnabled = selfSignedCertificateCheckBox.isChecked,
                    selfSignedCertificate = selfSignedCertificateEdit.text?.toString() ?: "",
                    clientCertificateEnabled = clientCertificateCheckBox.isChecked,
                    clientCertificate = clientCertificateEdit.text?.toString() ?: ""
                )
            }
        }
    }

    private class GetPemFileContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent {
            return Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/x-pem-file", ClipDescription.MIMETYPE_TEXT_PLAIN)
                )
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
            null,
            Proxy.Type.HTTP,
            Proxy.Type.SOCKS
        )
    }

    private lateinit var mainModel: ServerEditFragmentViewModel
    private lateinit var proxyTypeItemValues: Array<String>

    private val binding by viewLifecycleObject(ServerEditProxyFragmentBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        proxyTypeItemValues = resources.getStringArray(R.array.proxy_type_items)
        mainModel = ServerEditFragmentViewModel.get(navController)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        with(binding) {
            proxyTypeView.setAdapter(ArrayDropdownAdapter(proxyTypeItemValues))
            proxyTypeView.setOnItemClickListener { _, _, position, _ ->
                setEditable(proxyTypeItems[position] != null)
            }

            portEdit.handleNumberRangeError(Server.portRange)

            val model =
                ViewModelProvider(this@ServerProxySettingsFragment)[ServerProxySettingsFragmentModel::class.java]
            if (!model.populatedUiFromServer) {
                with(mainModel.server) {
                    proxyTypeView.setText(proxyTypeItemValues[proxyTypeItems.indexOf(proxyType)])
                    addressEdit.setText(proxyHostname)
                    portEdit.setText(proxyPort.toString())
                    usernameEdit.setText(proxyUser)
                    passwordEdit.setText(proxyPassword)

                    if (proxyType == null) {
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
                mainModel.server = mainModel.server.copy(
                    proxyType = proxyTypeItemValues.indexOf(proxyTypeView.text.toString())
                        .takeIf { it != -1 }?.let(proxyTypeItems::get),
                    proxyHostname = addressEdit.text?.toString() ?: "",
                    proxyPort = portEdit.text?.toString()?.toIntOrNull() ?: 0,
                    proxyUser = usernameEdit.text?.toString() ?: "",
                    proxyPassword = passwordEdit.text?.toString() ?: ""
                )
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

private fun EditText.checkLength(emptyFieldError: CharSequence) {
    when {
        text.trimmedLength() == 0 -> textInputLayout.error = emptyFieldError
        textInputLayout.error == emptyFieldError -> textInputLayout.error = null
    }
}

private val EditText.dontHaveError: Boolean
    get() = textInputLayout.error == null
