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

package org.equeim.tremotesf

import android.app.Dialog
import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.Keep

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat


class SettingsFragment : NavigationFragment(R.layout.settings_fragment,
                                            R.string.settings) {
    @Keep
    class PreferenceFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var persistentNotificationKey: String

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            updateBackgroundUpdatePreference()

            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            persistentNotificationKey = getString(R.string.prefs_persistent_notification_key)
            findPreference<Preference>(persistentNotificationKey)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    findNavController().navigate(R.id.action_settingsFragment_to_persistentNotificationWarningFragment)
                    false
                } else {
                    true
                }
            }
        }

        override fun onDestroy() {
            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                getString(R.string.prefs_notify_on_finished_key),
                getString(R.string.prefs_notify_on_added_key),
                persistentNotificationKey -> updateBackgroundUpdatePreference()
            }
        }

        fun enablePersistentNotification() {
            findPreference<CheckBoxPreference>(persistentNotificationKey)?.isChecked = true
        }

        private fun updateBackgroundUpdatePreference() {
            findPreference<Preference>(getString(R.string.prefs_background_update_interval_key))
                    ?.isEnabled = (Settings.notifyOnFinished || Settings.notifyOnAdded) && !Settings.showPersistentNotification
        }

        class PersistentNotificationWarningFragment : DialogFragment() {
            override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
                return AlertDialog.Builder(requireContext())
                        .setMessage(R.string.persistent_notification_warning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            ((parentFragmentManager.primaryNavigationFragment as? SettingsFragment)?.childFragmentManager?.findFragmentById(R.id.content_frame) as PreferenceFragment?)
                                    ?.enablePersistentNotification()
                        }
                        .create()
            }
        }
    }
}
