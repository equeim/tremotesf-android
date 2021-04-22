/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.NavDirections
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.NavigationDialogFragment
import org.equeim.tremotesf.ui.NavigationFragment
import org.equeim.tremotesf.utils.Logger

class RuntimePermissionHelper(
    private val permission: String,
    private val permissionRationaleDialogDirections: NavDirections,
    private val permissionSettingsDialogDirections: NavDirections
) : Logger {
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> by ::_permissionGranted

    fun registerWithFragment(fragment: NavigationFragment): ActivityResultLauncher<String> {
        val launcher =
            fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                _permissionGranted.value = granted
                if (granted) {
                    info("Permission $permission granted")
                } else {
                    info("Permission $permission not granted")
                    info("Showing rationale for going to permission settings")
                    fragment.navigate(permissionSettingsDialogDirections)
                }
            }

        fragment.setFragmentResultListener(RuntimePermissionRationaleDialog.RESULT_KEY) { _, _ ->
            requestPermission(launcher)
        }

        return launcher
    }

    fun checkPermission(context: Context): Boolean {
        info("Checking permission $permission")
        if (ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            info("Permission is already granted")
            _permissionGranted.value = true
            return true
        }
        info("Permission is not granted")
        return false
    }

    fun requestPermission(
        fragment: NavigationFragment,
        launcher: ActivityResultLauncher<String>
    ) {
        if (checkPermission(fragment.requireContext())) return
        if (fragment.shouldShowRequestPermissionRationale(permission)) {
            info("Showing rationale for requesting permission")
            fragment.navigate(permissionRationaleDialogDirections)
        } else {
            requestPermission(launcher)
        }
    }

    private fun requestPermission(launcher: ActivityResultLauncher<String>) {
        info("Requesting permission $permission from system")
        try {
            launcher.launch(permission)
        } catch (e: ActivityNotFoundException) {
            error("Failed to start activity", e)
        }
    }
}

class RuntimePermissionRationaleDialog : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args by navArgs<RuntimePermissionRationaleDialogArgs>()
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(args.permissionRationaleText)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            }
            .create()
    }

    companion object {
        val RESULT_KEY = RuntimePermissionRationaleDialog::class.qualifiedName!!
    }
}

class RuntimePermissionSystemSettingsDialog : NavigationDialogFragment(), Logger {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .apply {
                val args by navArgs<RuntimePermissionRationaleDialogArgs>()
                var msg =
                    "${args.permissionRationaleText}\n\n${getText(R.string.runtime_permission_go_to_settings_rationale)}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    msg += "\n\n${requireContext().packageManager.backgroundPermissionOptionLabel}"
                }
                setMessage(msg)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.go_to_settings) { _, _ -> goToPermissionSettings() }
            .create()
    }

    private fun goToPermissionSettings() {
        info("Going to application's permission settings activity")
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            error("Failed to start activity", e)
        }
    }
}
