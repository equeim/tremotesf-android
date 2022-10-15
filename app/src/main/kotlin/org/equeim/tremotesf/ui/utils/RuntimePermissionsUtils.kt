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

package org.equeim.tremotesf.ui.utils

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.NavMainDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.MutableEventFlow
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.navigate
import timber.log.Timber

class RuntimePermissionHelper(
    private val requiredPermission: String,
    @StringRes private val permissionRationaleStringId: Int,
    private val showRationaleBeforeRequesting: Boolean = true,
    private val requestPermissions: List<String> = listOf(requiredPermission)
) {
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> by ::_permissionGranted

    private val _permissionRequestResult = MutableEventFlow<Boolean>()
    val permissionRequestResult: Flow<Boolean> by ::_permissionRequestResult

    fun registerWithFragment(fragment: Fragment, navigationFragment: Fragment = fragment): ActivityResultLauncher<Array<String>> {
        val launcher =
            fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
                val granted = grantResults[requiredPermission] == true
                _permissionGranted.value = granted
                _permissionRequestResult.tryEmit(granted)
                if (granted) {
                    Timber.i("Permission $requiredPermission granted")
                } else {
                    Timber.i("Permission $requiredPermission not granted")
                    Timber.i("Showing rationale for going to permission settings")
                    navigationFragment.navigate(
                        NavMainDirections.toRuntimePermissionSystemSettingsDialog(
                            permissionRationaleStringId
                        )
                    )
                }
            }

        navigationFragment.setFragmentResultListener(RuntimePermissionRationaleDialog.RESULT_KEY) { _, _ ->
            requestPermission(launcher)
        }

        return launcher
    }

    fun checkPermission(context: Context): Boolean {
        Timber.i("Checking permission $requiredPermission")
        if (ContextCompat.checkSelfPermission(
                context,
                requiredPermission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Timber.i("Permission is already granted")
            _permissionGranted.value = true
            return true
        }
        Timber.i("Permission is not granted")
        return false
    }

    fun requestPermission(
        fragment: Fragment,
        launcher: ActivityResultLauncher<Array<String>>,
        navigationFragment: Fragment = fragment,
    ) {
        if (checkPermission(fragment.requireContext())) {
            _permissionRequestResult.tryEmit(true)
            return
        }
        if (showRationaleBeforeRequesting && fragment.shouldShowRequestPermissionRationale(requiredPermission)) {
            Timber.i("Showing rationale for requesting permission")
            navigationFragment.navigate(
                NavMainDirections.toRuntimePermissionRationaleDialog(
                    permissionRationaleStringId
                )
            )
        } else {
            requestPermission(launcher)
        }
    }

    private fun requestPermission(launcher: ActivityResultLauncher<Array<String>>) {
        Timber.i("Requesting permissions $requestPermissions from system")
        try {
            launcher.launch(requestPermissions.toTypedArray())
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
    }
}

class RuntimePermissionRationaleDialog : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = RuntimePermissionRationaleDialogArgs.fromBundle(requireArguments())
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(args.permissionRationaleStringId)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.request_permission) { _, _ ->
                setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            }
            .create()
    }

    companion object {
        val RESULT_KEY = RuntimePermissionRationaleDialog::class.qualifiedName!!
    }
}

class RuntimePermissionSystemSettingsDialog : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = RuntimePermissionRationaleDialogArgs.fromBundle(requireArguments())
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage("${getText(args.permissionRationaleStringId)}\n\n${getText(R.string.runtime_permission_go_to_settings_rationale)}")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.go_to_settings) { _, _ -> goToPermissionSettings() }
            .create()
    }

    private fun goToPermissionSettings() {
        Timber.i("Going to application's permission settings activity")
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Failed to start activity")
        }
    }
}
