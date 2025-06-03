// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelperState.ShowDialog.None
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelperState.ShowDialog.PermissionRationale
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelperState.ShowDialog.RejectionMessage
import timber.log.Timber

@Composable
fun TremotesfRuntimePermissionHelper(
    state: TremotesfRuntimePermissionHelperState,
    @StringRes permissionRationaleText: Int
) {
    when (state.showDialog) {
        PermissionRationale -> AlertDialog(
            onDismissRequest = state::onDismissedDialog,
            confirmButton = {
                TextButton(onClick = state::onAgreedToRequestPermission) { Text(stringResource(R.string.request_permission)) }
            },
            dismissButton = {
                TextButton(onClick = state::onDismissedDialog) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { Text(stringResource(permissionRationaleText)) }
        )

        RejectionMessage -> AlertDialog(
            onDismissRequest = state::onDismissedDialog,
            confirmButton = {
                TextButton(onClick = state::onAgreedToGoToSystemSettings) { Text(stringResource(R.string.go_to_settings)) }
            },
            dismissButton = {
                TextButton(onClick = state::onDismissedDialog) { Text(stringResource(android.R.string.cancel)) }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                    Text(stringResource(permissionRationaleText))
                    Text(stringResource(R.string.runtime_permission_go_to_settings_rationale))
                }
            }
        )

        None -> Unit
    }
}

@Composable
fun rememberTremotesfRuntimePermissionHelperState(
    requiredPermission: String,
    showRationaleBeforeRequesting: Boolean,
    permissionsToRequest: List<String> = listOf(requiredPermission),
    onPermissionGranted: () -> Unit = {},
): TremotesfRuntimePermissionHelperState {
    val activity = LocalActivity.current
    val saver = Saver<TremotesfRuntimePermissionHelperState, TremotesfRuntimePermissionHelperState.ShowDialog>(
        save = { it.showDialog },
        restore = {
            TremotesfRuntimePermissionHelperState(
                activity = activity,
                requiredPermission = requiredPermission,
                showRationaleBeforeRequesting = showRationaleBeforeRequesting,
                permissionsToRequest = permissionsToRequest,
                onPermissionGranted = onPermissionGranted,
                initialShowDialog = it
            )
        }
    )
    val state = rememberSaveable(
        activity,
        requiredPermission,
        showRationaleBeforeRequesting,
        permissionsToRequest,
        onPermissionGranted,
        saver = saver
    ) {
        TremotesfRuntimePermissionHelperState(
            activity = activity,
            requiredPermission = requiredPermission,
            showRationaleBeforeRequesting = showRationaleBeforeRequesting,
            onPermissionGranted = onPermissionGranted,
            permissionsToRequest = permissionsToRequest,
        )
    }
    state.launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), state::onActivityResult)
    LifecycleEventEffect(event = Lifecycle.Event.ON_START, onEvent = state::checkPermission)
    return state
}

@Stable
class TremotesfRuntimePermissionHelperState(
    private val activity: Activity?,
    private val requiredPermission: String,
    private val showRationaleBeforeRequesting: Boolean,
    private val permissionsToRequest: List<String> = listOf(requiredPermission),
    private val onPermissionGranted: () -> Unit,
    initialShowDialog: ShowDialog = None,
) {
    var permissionGranted: Boolean by mutableStateOf(false)
        private set
    var showDialog: ShowDialog by mutableStateOf(initialShowDialog)
        private set

    var launcher: ActivityResultLauncher<Array<String>>? = null

    init {
        checkPermission()
    }

    fun onActivityResult(results: Map<String, Boolean>) {
        Timber.i("onActivityResult() called with: results = $results")
        val granted = results[requiredPermission]
        if (granted == null) {
            Timber.e("Requested permission $requiredPermission is not presents in results map")
            return
        }
        permissionGranted = granted
        if (permissionGranted) {
            onPermissionGranted()
        } else {
            showDialog = RejectionMessage
        }
    }

    fun checkPermission() {
        activity ?: return
        permissionGranted = (activity.checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED)
        Timber.i("Permission granted = $permissionGranted")
    }

    fun requestPermission() {
        activity ?: return
        checkPermission()
        if (permissionGranted) {
            Timber.i("Permission is already granted")
            return
        }
        if (showRationaleBeforeRequesting && activity.shouldShowRequestPermissionRationale(requiredPermission)) {
            showDialog = PermissionRationale
            return
        }
        actuallyRequestPermission()
    }

    fun onAgreedToRequestPermission() {
        Timber.i("onAgreedToRequestPermission() called")
        showDialog = None
        actuallyRequestPermission()
    }

    fun onAgreedToGoToSystemSettings() {
        Timber.i("onAgreedToGoToSystemSettings() called")
        activity ?: return
        showDialog = None
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "onAgreedToGoToSystemSettings: failed to start activity")
        }
    }

    fun onDismissedDialog() {
        Timber.i("onDismissedDialog() called")
        showDialog = None
    }

    private fun actuallyRequestPermission() {
        Timber.i("Requesting permissions $permissionsToRequest from system")
        try {
            checkNotNull(launcher).launch(permissionsToRequest.toTypedArray())
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "actuallyRequestPermission: failed to start activity")
        }
    }

    enum class ShowDialog {
        None,
        PermissionRationale,
        RejectionMessage,
    }
}
