// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.connectionsettingsfragment

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.equeim.tremotesf.BuildConfig
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.Server
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Error
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfNumberInputField
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelper
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.components.UNSIGNED_16BIT_RANGE
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.components.rememberTremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.rememberTremotesfRuntimePermissionHelperState
import org.equeim.tremotesf.ui.utils.safeNavigate
import timber.log.Timber
import java.io.FileNotFoundException
import java.net.Proxy
import kotlin.time.Duration.Companion.seconds

class ServerEditFragment : ComposeFragment() {
    private val args: ServerEditFragmentArgs by navArgs()

    @Composable
    override fun Content(navController: NavController) {
        val model =
            viewModel<ServerEditFragmentViewModel>(initializer = ServerEditFragmentViewModel.initializer(navController))
        LifecycleEventEffect(event = Lifecycle.Event.ON_START, onEvent = model::checkIfLocationEnabled)
        ServerEditScreen(
            navigateUp = navController::navigateUp,
            editingServer = args.server != null,
            name = model.name,
            address = model.address,
            port = model.port,
            httpsEnabled = model.httpsEnabled,
            apiPath = model.apiPath,
            authentication = model.authentication,
            username = model.username,
            password = model.password,
            updateInterval = model.updateInterval,
            timeout = model.timeout,
            autoConnectOnWifiNetworkEnabled = model.autoConnectOnWifiNetworkEnabled,
            autoConnectOnWifiNetworkSSID = model.autoConnectOnWifiNetworkSSID,
            showSSIDErrorMessage = model.showSSIDErrorMessage,
            locationEnabled = model.locationEnabled.collectAsStateWithLifecycle(),
            navigateToProxySettings = { navController.safeNavigate(ServerEditFragmentDirections.toProxySettingsFragment()) },
            navigateToCertificatesSettings = { navController.safeNavigate(ServerEditFragmentDirections.toCertificatesFragment()) },
            setSSIDFromCurrentNetwork = model::setSSIDFromCurrentNetwork,
            isAboutToOverwriteServer = model::isAboutToOverwriteServer,
            saveServer = model::saveServer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerEditScreen(
    navigateUp: () -> Unit,
    editingServer: Boolean,
    name: MutableState<String>,
    address: MutableState<String>,
    port: TremotesfIntegerNumberInputFieldState,
    httpsEnabled: MutableState<Boolean>,
    apiPath: MutableState<String>,
    authentication: MutableState<Boolean>,
    username: MutableState<String>,
    password: MutableState<String>,
    updateInterval: TremotesfIntegerNumberInputFieldState,
    timeout: TremotesfIntegerNumberInputFieldState,
    autoConnectOnWifiNetworkEnabled: MutableState<Boolean>,
    autoConnectOnWifiNetworkSSID: MutableState<String>,
    showSSIDErrorMessage: MutableState<Boolean>,
    locationEnabled: State<Boolean>,
    navigateToProxySettings: () -> Unit,
    navigateToCertificatesSettings: () -> Unit,
    setSSIDFromCurrentNetwork: () -> Unit,
    isAboutToOverwriteServer: () -> Boolean,
    saveServer: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    if (showSSIDErrorMessage.value) {
        val text = stringResource(R.string.current_ssid_error)
        LaunchedEffect(null) {
            snackbarHostState.showSnackbar(message = text, withDismissAction = true, duration = SnackbarDuration.Long)
            showSSIDErrorMessage.value = false
        }
    }

    var showOverwriteDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverwriteDialog = false
                        saveServer()
                        navigateUp()
                    }
                ) { Text(stringResource(R.string.overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteDialog = false
                }) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { Text(stringResource(R.string.server_exists)) }
        )
    }

    var showEmptyNameError: Boolean by rememberSaveable { mutableStateOf(false) }
    var showEmptyAddressError: Boolean by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TremotesfTopAppBar(
                title = stringResource(if (editingServer) R.string.edit_server else R.string.add_server),
                navigateUp = navigateUp
            )
        },
        floatingActionButton = {
            val text = stringResource(if (editingServer) R.string.save else R.string.add)
            ExtendedFloatingActionButton(
                text = { Text(text) },
                icon = { Icon(Icons.Filled.Done, contentDescription = text) },
                expanded = !WindowInsets.isImeVisible,
                onClick = {
                    if (name.value.isBlank()) {
                        showEmptyNameError = true
                    }
                    if (address.value.isBlank()) {
                        showEmptyAddressError = true
                    }
                    if (!showEmptyNameError && !showEmptyAddressError) {
                        if (isAboutToOverwriteServer()) {
                            showOverwriteDialog = true
                        } else {
                            saveServer()
                            navigateUp()
                        }
                    }
                })
        },
        floatingActionButtonPosition = if (WindowInsets.isImeVisible) {
            FabPosition.End
        } else {
            FabPosition.Center
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(vertical = Dimens.screenContentPaddingVertical())
                .padding(bottom = Dimens.PaddingForFAB),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val horizontalPadding = Dimens.screenContentPaddingHorizontal()

            val shouldRequestFocus = rememberSaveable { name.value.isEmpty() }
            val focusRequester = if (shouldRequestFocus) {
                rememberTremotesfInitialFocusRequester()
            } else {
                null
            }

            OutlinedTextField(
                value = name.value,
                onValueChange = {
                    name.value = it
                    if (it.isNotBlank()) {
                        showEmptyNameError = false
                    }
                },
                label = { Text(stringResource(R.string.name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                isError = showEmptyNameError,
                supportingText = if (showEmptyNameError) {
                    { Text(stringResource(R.string.empty_field_error)) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .run {
                        if (focusRequester != null) {
                            focusRequester(focusRequester)
                        } else {
                            this
                        }
                    }
            )

            OutlinedTextField(
                value = address.value,
                onValueChange = {
                    address.value = it
                    if (it.isNotBlank()) {
                        showEmptyAddressError = false
                    }
                },
                label = { Text(stringResource(R.string.address_edit_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine = true,
                isError = showEmptyAddressError,
                supportingText = if (showEmptyAddressError) {
                    { Text(stringResource(R.string.empty_field_error)) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfNumberInputField(
                state = port,
                range = UNSIGNED_16BIT_RANGE,
                label = R.string.port,
                imeAction = ImeAction.Next,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfSwitchWithText(
                checked = httpsEnabled.value,
                text = R.string.use_https_protocol,
                onCheckedChange = httpsEnabled::value::set,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            Text(
                text = stringResource(R.string.https_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            OutlinedTextField(
                value = apiPath.value,
                onValueChange = apiPath::value::set,
                label = { Text(stringResource(R.string.api_path)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier
                    .padding(top = Dimens.SpacingSmall)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            OutlinedButton(
                onClick = navigateToProxySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            ) { Text(stringResource(R.string.proxy_settings)) }

            OutlinedButton(
                onClick = navigateToCertificatesSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            ) { Text(stringResource(R.string.certificates)) }

            TremotesfSwitchWithText(
                checked = authentication.value,
                text = R.string.authentication,
                onCheckedChange = authentication::value::set,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            OutlinedTextField(
                value = username.value,
                onValueChange = username::value::set,
                label = { Text(stringResource(R.string.username)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = authentication.value,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
                    .padding(horizontal = horizontalPadding)
            )

            OutlinedTextField(
                value = password.value,
                onValueChange = password::value::set,
                label = { Text(stringResource(R.string.password)) },
                enabled = authentication.value,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfNumberInputField(
                state = updateInterval,
                range = Server.updateIntervalRangeInSeconds,
                label = R.string.update_interval,
                imeAction = ImeAction.Next,
                suffix = R.string.text_field_suffix_seconds,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            Text(
                text = stringResource(R.string.update_interval_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfNumberInputField(
                state = timeout,
                range = Server.timeoutRangeInSeconds,
                label = R.string.timeout,
                imeAction = if (autoConnectOnWifiNetworkEnabled.value) ImeAction.Next else ImeAction.Unspecified,
                suffix = R.string.text_field_suffix_seconds,
                modifier = Modifier
                    .padding(top = Dimens.SpacingSmall)
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            var showDialogToEnableLocation: Boolean by rememberSaveable { mutableStateOf(false) }
            if (showDialogToEnableLocation) {
                val context = LocalContext.current
                AlertDialog(
                    onDismissRequest = { showDialogToEnableLocation = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialogToEnableLocation = false
                            Timber.i("Going to system location settings activity")
                            try {
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(e, "Failed to start activity")
                            }
                        }) {
                            Text(stringResource(R.string.go_to_settings))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialogToEnableLocation = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                    text = { Text(stringResource(R.string.request_enable_location)) }
                )
            }

            val locationPermissionHelperState = rememberTremotesfRuntimePermissionHelperState(
                requiredPermission = REQUIRED_LOCATION_PERMISSION,
                showRationaleBeforeRequesting = true,
                permissionsToRequest = LOCATION_PERMISSIONS_TO_REQUEST
            ) {
                if (!locationEnabled.value && LOCATION_NEEDS_TO_BE_ENABLED) {
                    showDialogToEnableLocation = true
                }
            }

            TremotesfRuntimePermissionHelper(locationPermissionHelperState, R.string.location_permission_rationale)

            TremotesfSwitchWithText(
                checked = autoConnectOnWifiNetworkEnabled.value,
                text = R.string.auto_connect_on_wifi_network,
                onCheckedChange = {
                    autoConnectOnWifiNetworkEnabled.value = it
                    if (it && !locationPermissionHelperState.permissionGranted) {
                        locationPermissionHelperState.requestPermission()
                    }
                },
                modifier = Modifier
                    .padding(top = Dimens.SpacingSmall)
                    .fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            val locationErrorButtonState: LocationErrorButtonState by remember {
                derivedStateOf {
                    when {
                        !locationPermissionHelperState.permissionGranted -> LocationErrorButtonState.NoPermission
                        LOCATION_NEEDS_TO_BE_ENABLED && !locationEnabled.value -> LocationErrorButtonState.LocationDisabled
                        else -> LocationErrorButtonState.Hide
                    }
                }
            }

            if (locationErrorButtonState != LocationErrorButtonState.Hide) {
                OutlinedButton(
                    onClick = {
                        when (locationErrorButtonState) {
                            LocationErrorButtonState.NoPermission -> locationPermissionHelperState.requestPermission()
                            LocationErrorButtonState.LocationDisabled -> showDialogToEnableLocation = true
                            LocationErrorButtonState.Hide -> throw IllegalStateException()
                        }
                    },
                    enabled = autoConnectOnWifiNetworkEnabled.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                    ) {
                        Icon(Icons.Filled.Error, contentDescription = null)
                        Text(
                            stringResource(
                                when (locationErrorButtonState) {
                                    LocationErrorButtonState.NoPermission -> R.string.request_location_permission
                                    LocationErrorButtonState.LocationDisabled,
                                        -> R.string.enable_location

                                    else -> throw IllegalStateException()
                                }
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = autoConnectOnWifiNetworkSSID.value,
                onValueChange = autoConnectOnWifiNetworkSSID::value::set,
                enabled = autoConnectOnWifiNetworkEnabled.value,
                label = { Text(stringResource(R.string.wifi_ssid_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            OutlinedButton(
                onClick = setSSIDFromCurrentNetwork,
                enabled = autoConnectOnWifiNetworkEnabled.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            ) {
                Text(stringResource(R.string.wifi_ssid_set_from_current))
            }

            Text(
                text = stringResource(
                    if (CAN_REQUEST_BACKGROUND_LOCATION_PERMISSION) {
                        R.string.background_wifi_networks_explanation_with_background_permission
                    } else {
                        R.string.background_wifi_networks_explanation_no_background_permission
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            if (CAN_REQUEST_BACKGROUND_LOCATION_PERMISSION) {
                val backgroundLocationPermissionHelperState = rememberTremotesfRuntimePermissionHelperState(
                    requiredPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    showRationaleBeforeRequesting = true
                )
                TremotesfRuntimePermissionHelper(
                    state = backgroundLocationPermissionHelperState,
                    permissionRationaleText = R.string.background_location_permission_rationale
                )
                OutlinedButton(
                    onClick = {
                        if (!backgroundLocationPermissionHelperState.permissionGranted) {
                            backgroundLocationPermissionHelperState.requestPermission()
                        }
                    },
                    enabled = autoConnectOnWifiNetworkEnabled.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                ) {
                    Text(
                        stringResource(
                            if (backgroundLocationPermissionHelperState.permissionGranted) {
                                R.string.background_location_permission_granted
                            } else {
                                R.string.request_background_location_permission
                            }
                        )
                    )
                }
            }
        }
    }
}

private enum class LocationErrorButtonState {
    Hide,
    NoPermission,
    LocationDisabled
}

private val REQUIRED_LOCATION_PERMISSION: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Manifest.permission.ACCESS_FINE_LOCATION
} else {
    Manifest.permission.ACCESS_COARSE_LOCATION
}

private val LOCATION_PERMISSIONS_TO_REQUEST: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    )
} else {
    listOf(REQUIRED_LOCATION_PERMISSION)
}

private val LOCATION_NEEDS_TO_BE_ENABLED: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

@Suppress("KotlinConstantConditions")
private val CAN_REQUEST_BACKGROUND_LOCATION_PERMISSION: Boolean =
    !BuildConfig.GOOGLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@Preview
@Composable
private fun ServerEditScreenPreview() = ScreenPreview {
    ServerEditScreen(
        navigateUp = {},
        editingServer = true,
        name = remember { mutableStateOf("hmm") },
        address = remember { mutableStateOf("4.2.4.2") },
        port = rememberTremotesfIntegerNumberInputFieldState(42),
        httpsEnabled = remember { mutableStateOf(false) },
        apiPath = remember { mutableStateOf("/lol") },
        authentication = remember { mutableStateOf(false) },
        username = remember { mutableStateOf("") },
        password = remember { mutableStateOf("") },
        updateInterval = rememberTremotesfIntegerNumberInputFieldState(Server.DEFAULT_UPDATE_INTERVAL.inWholeSeconds),
        timeout = rememberTremotesfIntegerNumberInputFieldState(Server.DEFAULT_TIMEOUT.inWholeSeconds),
        autoConnectOnWifiNetworkEnabled = remember { mutableStateOf(false) },
        autoConnectOnWifiNetworkSSID = remember { mutableStateOf("") },
        showSSIDErrorMessage = remember { mutableStateOf(false) },
        locationEnabled = remember { mutableStateOf(false) },
        navigateToProxySettings = {},
        navigateToCertificatesSettings = {},
        setSSIDFromCurrentNetwork = {},
        isAboutToOverwriteServer = { false },
        saveServer = {}
    )
}

@OptIn(SavedStateHandleSaveableApi::class)
class ServerEditFragmentViewModel(
    private val args: ServerEditFragmentArgs,
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val editingServer: Server =
        if (args.server != null) {
            GlobalServers.serversState.value.servers.find { it.name == args.server } ?: Server()
        } else {
            Server()
        }

    val name by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.name) }
    val address by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.address) }
    val port by savedStateHandle.saveable(saver = TremotesfIntegerNumberInputFieldState.Saver()) {
        TremotesfIntegerNumberInputFieldState((editingServer.port).toLong())
    }
    val httpsEnabled by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(editingServer.httpsEnabled) }
    val apiPath by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.apiPath) }
    val authentication by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(editingServer.authentication) }
    val username by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.username) }
    val password by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.password) }
    val updateInterval by savedStateHandle.saveable(saver = TremotesfIntegerNumberInputFieldState.Saver()) {
        TremotesfIntegerNumberInputFieldState(editingServer.updateInterval.inWholeSeconds)
    }
    val timeout by savedStateHandle.saveable(saver = TremotesfIntegerNumberInputFieldState.Saver()) {
        TremotesfIntegerNumberInputFieldState((editingServer.timeout).inWholeSeconds)
    }
    val autoConnectOnWifiNetworkEnabled by savedStateHandle.saveable<MutableState<Boolean>> {
        mutableStateOf(editingServer.autoConnectOnWifiNetworkEnabled)
    }
    val autoConnectOnWifiNetworkSSID by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.autoConnectOnWifiNetworkSSID) }
    val showSSIDErrorMessage by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(false) }

    val selfSignedCertificateEnabled by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(editingServer.selfSignedCertificateEnabled) }
    val selfSignedCertificate by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.selfSignedCertificate) }
    val clientCertificateEnabled by savedStateHandle.saveable<MutableState<Boolean>> { mutableStateOf(editingServer.clientCertificateEnabled) }
    val clientCertificate by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.clientCertificate) }

    val proxyType by savedStateHandle.saveable<MutableState<Proxy.Type?>> { mutableStateOf(editingServer.proxyType) }
    val proxyHostname by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.proxyHostname) }
    val proxyPort by savedStateHandle.saveable(saver = TremotesfIntegerNumberInputFieldState.Saver()) {
        TremotesfIntegerNumberInputFieldState(editingServer.proxyPort.toLong())
    }
    val proxyUser by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.proxyUser) }
    val proxyPassword by savedStateHandle.saveable<MutableState<String>> { mutableStateOf(editingServer.proxyPassword) }

    private val _locationEnabled = MutableStateFlow(isLocationEnabled())
    val locationEnabled: StateFlow<Boolean> by ::_locationEnabled

    private fun isLocationEnabled(): Boolean {
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

    fun setSSIDFromCurrentNetwork() {
        viewModelScope.launch {
            val ssid = GlobalServers.wifiNetworkController.getCurrentWifiSsid()
            if (ssid != null) {
                autoConnectOnWifiNetworkSSID.value = ssid
            } else {
                showSSIDErrorMessage.value = true
            }
        }
    }

    fun loadSelfSignedCertificateFromFile(uri: Uri) {
        viewModelScope.launch { loadCertificateFromFile(uri)?.let(selfSignedCertificate::value::set) }
    }

    fun loadClientCertificateFromFile(uri: Uri) {
        viewModelScope.launch { loadCertificateFromFile(uri)?.let(clientCertificate::value::set) }
    }

    private suspend fun loadCertificateFromFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            if (stream != null) {
                stream.use { it.reader().readText() }
            } else {
                Timber.e("loadCertificateFromFile: failed to read certificate, ContentResolver returned null InputStream")
                null
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "loadCertificateFromFile: failed to read certificate")
            null
        }
    }

    fun isAboutToOverwriteServer(): Boolean {
        return name.value != editingServer.name
                && GlobalServers.serversState.value.servers.any { it.name == name.value }
    }

    fun saveServer() {
        val server = editingServer.copy(
            name = name.value.trim(),
            address = address.value.trim(),
            port = port.numberValue.toInt(),
            apiPath = apiPath.value.trim(),

            proxyType = proxyType.value,
            proxyHostname = proxyHostname.value.trim(),
            proxyPort = proxyPort.numberValue.toInt(),
            proxyUser = proxyUser.value.trim(),
            proxyPassword = proxyPassword.value.trim(),

            httpsEnabled = httpsEnabled.value,
            selfSignedCertificateEnabled = selfSignedCertificateEnabled.value,
            selfSignedCertificate = selfSignedCertificate.value.trim(),
            clientCertificateEnabled = clientCertificateEnabled.value,
            clientCertificate = clientCertificate.value.trim(),

            authentication = authentication.value,
            username = username.value.trim(),
            password = password.value.trim(),

            updateInterval = updateInterval.numberValue.seconds,
            timeout = timeout.numberValue.seconds,

            autoConnectOnWifiNetworkEnabled = autoConnectOnWifiNetworkEnabled.value,
            autoConnectOnWifiNetworkSSID = autoConnectOnWifiNetworkSSID.value.trim()
        )
        if (args.server == null || server != editingServer) {
            GlobalServers.addOrReplaceServer(server, previousName = args.server)
        } else {
            Timber.d("saveServer: server did not change")
        }
    }

    companion object {
        fun initializer(navController: NavController): CreationExtras.() -> ServerEditFragmentViewModel {
            return {
                ServerEditFragmentViewModel(
                    args = ServerEditFragmentArgs.fromBundle(checkNotNull(navController.getBackStackEntry(R.id.server_edit_fragment).arguments)),
                    application = checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}

class ServerCertificatesFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model =
            viewModel<ServerEditFragmentViewModel>(
                viewModelStoreOwner = navController.getBackStackEntry(R.id.server_edit_fragment),
                initializer = ServerEditFragmentViewModel.initializer(navController)
            )
        ServerCertificatesScreen(
            navigateUp = navController::navigateUp,
            selfSignedCertificateEnabled = model.selfSignedCertificateEnabled,
            selfSignedCertificate = model.selfSignedCertificate,
            clientCertificateEnabled = model.clientCertificateEnabled,
            clientCertificate = model.clientCertificate,
            loadSelfSignedCertificateFromFile = model::loadSelfSignedCertificateFromFile,
            loadClientCertificateFromFile = model::loadClientCertificateFromFile
        )
    }
}

@Composable
private fun ServerCertificatesScreen(
    navigateUp: () -> Unit,
    selfSignedCertificateEnabled: MutableState<Boolean>,
    selfSignedCertificate: MutableState<String>,
    clientCertificateEnabled: MutableState<Boolean>,
    clientCertificate: MutableState<String>,
    loadSelfSignedCertificateFromFile: (Uri) -> Unit,
    loadClientCertificateFromFile: (Uri) -> Unit,
) {
    Scaffold(topBar = {
        TremotesfTopAppBar(stringResource(R.string.proxy_settings), navigateUp)
    }) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                // Apply keyboard padding before scrolling to that text field is brought into view when focused
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(vertical = Dimens.screenContentPaddingVertical()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val horizontalPadding = Dimens.screenContentPaddingHorizontal()
            TremotesfSwitchWithText(
                checked = selfSignedCertificateEnabled.value,
                text = R.string.server_uses_self_signed_certificate,
                onCheckedChange = selfSignedCertificateEnabled::value::set,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            OutlinedTextField(
                value = selfSignedCertificate.value,
                onValueChange = selfSignedCertificate::value::set,
                label = { Text(stringResource(R.string.certificate)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = selfSignedCertificateEnabled.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .height(128.dp),
            )

            val selfSignedCertificateLauncher = rememberLauncherForActivityResult(GetPemFileContract) {
                it?.let(loadSelfSignedCertificateFromFile)
            }

            Button(
                onClick = {
                    try {
                        selfSignedCertificateLauncher.launch()
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e, "Failed to start activity")
                    }
                },
                enabled = selfSignedCertificateEnabled.value,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.load_from_file))
            }

            TremotesfSwitchWithText(
                checked = clientCertificateEnabled.value,
                text = R.string.use_client_certificate_authentication,
                onCheckedChange = clientCertificateEnabled::value::set,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            OutlinedTextField(
                value = clientCertificate.value,
                onValueChange = clientCertificate::value::set,
                label = { Text(stringResource(R.string.certificate)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = selfSignedCertificateEnabled.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .height(128.dp),
            )

            val clientCertificateLauncher = rememberLauncherForActivityResult(GetPemFileContract) {
                it?.let(loadClientCertificateFromFile)
            }

            Button(
                onClick = {
                    try {
                        clientCertificateLauncher.launch()
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e, "Failed to start activity")
                    }
                },
                enabled = clientCertificateEnabled.value,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.load_from_file))
            }
        }
    }
}

@Preview
@Composable
private fun ServerCertificatesScreenPreview() = ScreenPreview {
    ServerCertificatesScreen(
        navigateUp = {},
        selfSignedCertificateEnabled = remember { mutableStateOf(true) },
        selfSignedCertificate = remember { mutableStateOf("") },
        clientCertificateEnabled = remember { mutableStateOf(false) },
        clientCertificate = remember { mutableStateOf("") },
        loadSelfSignedCertificateFromFile = {},
        loadClientCertificateFromFile = {}
    )
}

private object GetPemFileContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf("application/x-pem-file", ClipDescription.MIMETYPE_TEXT_PLAIN)
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
    }
}

class ServerProxySettingsFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model =
            viewModel<ServerEditFragmentViewModel>(
                viewModelStoreOwner = navController.getBackStackEntry(R.id.server_edit_fragment),
                initializer = ServerEditFragmentViewModel.initializer(navController)
            )
        ServerProxySettingsScreen(
            navigateUp = navController::navigateUp,
            proxyType = model.proxyType,
            proxyHostname = model.proxyHostname,
            proxyPort = model.proxyPort,
            proxyUser = model.proxyUser,
            proxyPassword = model.proxyPassword
        )
    }
}

@Composable
private fun ServerProxySettingsScreen(
    navigateUp: () -> Unit,
    proxyType: MutableState<Proxy.Type?>,
    proxyHostname: MutableState<String>,
    proxyPort: TremotesfIntegerNumberInputFieldState,
    proxyUser: MutableState<String>,
    proxyPassword: MutableState<String>,
) {
    Scaffold(topBar = {
        TremotesfTopAppBar(stringResource(R.string.proxy_settings), navigateUp)
    }) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                // Apply keyboard padding before scrolling to that text field is brought into view when focused
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(Dimens.screenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            TremotesfComboBox(
                currentItem = proxyType::value,
                updateCurrentItem = proxyType::value::set,
                items = remember { listOf(null, Proxy.Type.HTTP, Proxy.Type.SOCKS) },
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            Proxy.Type.HTTP -> R.string.proxy_type_http
                            Proxy.Type.SOCKS -> R.string.proxy_type_socks5
                            else -> R.string.proxy_type_default
                        }
                    )
                },
                label = R.string.proxy_type,
                modifier = Modifier.fillMaxWidth()
            )

            val enableFields: Boolean by remember { derivedStateOf { proxyType.value != null } }

            OutlinedTextField(
                value = proxyHostname.value,
                onValueChange = proxyHostname::value::set,
                label = { Text(stringResource(R.string.address_edit_hint)) },
                enabled = enableFields,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            TremotesfNumberInputField(
                state = proxyPort,
                range = UNSIGNED_16BIT_RANGE,
                label = R.string.port,
                enabled = enableFields,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = proxyUser.value,
                onValueChange = proxyUser::value::set,
                label = { Text(stringResource(R.string.username)) },
                enabled = enableFields,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = proxyPassword.value,
                onValueChange = proxyPassword::value::set,
                label = { Text(stringResource(R.string.password)) },
                enabled = enableFields,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ServerProxySettingsScreenPreview() = ScreenPreview {
    ServerProxySettingsScreen(
        navigateUp = {},
        proxyType = remember { mutableStateOf(null) },
        proxyHostname = remember { mutableStateOf("") },
        proxyPort = rememberTremotesfIntegerNumberInputFieldState(),
        proxyUser = remember { mutableStateOf("") },
        proxyPassword = remember { mutableStateOf("") },
    )
}
