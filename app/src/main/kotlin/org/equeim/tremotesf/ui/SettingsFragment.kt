/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View

import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.equeim.tremotesf.R
import org.equeim.tremotesf.service.ForegroundService


class SettingsFragment : NavigationFragment(
    R.layout.settings_fragment,
    R.string.settings
) {
    @Keep
    class PreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            updateBackgroundUpdatePreference()

            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            findPreference<Preference>(Settings.persistentNotificationKey)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    navController.navigate(SettingsFragmentDirections.toPersistentNotificationWarningDialog())
                    false
                } else {
                    true
                }
            }

            requireParentFragment().setFragmentResultListener(SettingsPersistentNotificationWarningFragment.RESULT_KEY) { _, _ ->
                findPreference<CheckBoxPreference>(Settings.persistentNotificationKey)?.isChecked = true
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            addNavigationBarBottomPadding(forceViewForPadding = listView)
        }

        override fun onDestroy() {
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Settings.themeKey -> AppCompatDelegate.setDefaultNightMode(Settings.nightMode)

                Settings.oldColorsKey -> NavigationActivity.recreateAllActivities()

                Settings.notifyOnAddedKey,
                Settings.notifyOnFinishedKey -> updateBackgroundUpdatePreference()

                Settings.persistentNotificationKey -> {
                    updateBackgroundUpdatePreference()
                    if (Settings.showPersistentNotification) {
                        ForegroundService.start(requireContext())
                    } else {
                        ForegroundService.stop(requireContext())
                    }
                }
            }
        }

        private fun updateBackgroundUpdatePreference() {
            findPreference<Preference>(Settings.backgroundUpdateIntervalKey)
                ?.isEnabled =
                (Settings.notifyOnFinished || Settings.notifyOnAdded) && !Settings.showPersistentNotification
        }
    }
}

class SettingsPersistentNotificationWarningFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.persistent_notification_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setFragmentResult(RESULT_KEY, Bundle.EMPTY)
            }
            .create()
    }

    companion object {
        val RESULT_KEY = SettingsPersistentNotificationWarningFragment::class.qualifiedName!!
    }
}
