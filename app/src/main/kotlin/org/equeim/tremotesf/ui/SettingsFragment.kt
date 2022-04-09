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
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.Keep
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import kotlin.collections.set


class SettingsFragment : NavigationFragment(
    R.layout.settings_fragment,
    R.string.settings
) {
    @Keep
    class PreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is SettingsAppColorsPreference) {
                navigate(SettingsFragmentDirections.toColorThemeDialog())
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            updateBackgroundUpdatePreference()

            checkNotNull(preferenceManager.sharedPreferences).registerOnSharedPreferenceChangeListener(
                this
            )
            findPreference<Preference>(Settings.showPersistentNotification.key)?.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    navController.navigate(SettingsFragmentDirections.toPersistentNotificationWarningDialog())
                    false
                } else {
                    true
                }
            }

            requireParentFragment().setFragmentResultListener(
                SettingsPersistentNotificationWarningFragment.RESULT_KEY
            ) { _, _ ->
                findPreference<CheckBoxPreference>(Settings.showPersistentNotification.key)?.isChecked =
                    true
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)
            listView.tag = getText(R.string.add_navigation_bar_padding)
            addNavigationBarBottomPadding()
        }

        override fun onDestroy() {
            checkNotNull(preferenceManager.sharedPreferences).unregisterOnSharedPreferenceChangeListener(
                this
            )
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                Settings.notifyOnAdded.key,
                Settings.notifyOnFinished.key,
                Settings.showPersistentNotification.key -> updateBackgroundUpdatePreference()
            }
        }

        private fun updateBackgroundUpdatePreference() = lifecycleScope.launch {
            findPreference<Preference>(Settings.backgroundUpdateInterval.key)
                ?.isEnabled =
                (Settings.notifyOnFinished.get() || Settings.notifyOnAdded.get()) && !Settings.showPersistentNotification.get()
        }
    }
}

class SettingsPersistentNotificationWarningFragment : NavigationDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
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

class SettingsAppColorsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = R.style.Preference
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes)

class SettingsColorThemeFragment : NavigationDialogFragment() {
    private lateinit var themeFromSettings: Deferred<Settings.ColorTheme>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeFromSettings = lifecycleScope.async { Settings.colorTheme.get() }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.settings_color_theme_dialog, null)

        val viewsToTheme = mutableMapOf<View, Settings.ColorTheme>()
        val onViewClicked = { choiceView: View ->
            val theme = checkNotNull(viewsToTheme[choiceView])
            requiredActivity.lifecycleScope.launch { Settings.colorTheme.set(theme) }
            dismiss()
        }

        checkNotNull(view.findViewById(R.id.system_colors)).apply {
            if (DynamicColors.isDynamicColorAvailable()) {
                viewsToTheme[this] = Settings.ColorTheme.System
            } else {
                isVisible = false
            }
        }

        val choiceList = checkNotNull(view.findViewById<LinearLayout>(R.id.choice_list))
        for (theme in Settings.ColorTheme.values().asList().minus(Settings.ColorTheme.System)) {
            SettingsColorThemeColorView(requireContext()).apply {
                viewsToTheme[this] = theme
                setColorFromTheme(theme.activityThemeResId)
                choiceList.addView(
                    this,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }

        viewsToTheme.keys.forEach { it.setOnClickListener(onViewClicked) }

        lifecycleScope.launch {
            val themeFromSettings = this@SettingsColorThemeFragment.themeFromSettings.await()
            (viewsToTheme.entries.single { it.value == themeFromSettings }.key as Checkable)
                .isChecked = true
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle(R.string.prefs_color_theme_title)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}

private class SettingsColorThemeColorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = R.style.Widget_Tremotesf_SettingsColorThemeColorView
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes), Checkable {
    init {
        orientation = HORIZONTAL
        inflate(context, R.layout.settings_color_theme_color_view, this)
    }

    fun setColorFromTheme(@StyleRes activityThemeResId: Int) {
        val color = MaterialColors.getColor(
            ContextThemeWrapper(context, activityThemeResId),
            R.attr.colorPrimary,
            SettingsColorThemeColorView::class.simpleName
        )
        findViewById<View>(R.id.color_view).background.colorFilter =
            PorterDuffColorFilter(color, PorterDuff.Mode.ADD)
    }

    private var checked = false

    override fun setChecked(checked: Boolean) {
        this.checked = checked
        refreshDrawableState()
    }

    override fun isChecked() = checked

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isChecked) {
            mergeDrawableStates(drawableState, DRAWABLE_STATE_CHECKED)
        }
        return drawableState
    }

    private companion object {
        val DRAWABLE_STATE_CHECKED = intArrayOf(android.R.attr.state_checked)
    }
}
