// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.Manifest
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.utils.RuntimePermissionHelper
import org.equeim.tremotesf.ui.utils.updateCompoundDrawables
import kotlin.collections.set


class SettingsFragment : NavigationFragment(
    R.layout.settings_fragment,
    R.string.settings
) {
    class PreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val model: PreferenceFragmentViewModel by viewModels()
        private var notificationPermissionLauncher: ActivityResultLauncher<Array<String>>? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            // super.onCreate() calls onCreatePreferences() so we need to setup launcher before that
            notificationPermissionLauncher = model.notificationPermissionHelper?.registerWithFragment(this, requireParentFragment())
            model.notificationPermissionHelper?.checkPermission(requireContext())
            super.onCreate(savedInstanceState)
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is SettingsAppColorsPreference) {
                navigate(SettingsFragmentDirections.toColorThemeDialog())
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        override fun onStart() {
            super.onStart()
            model.notificationPermissionHelper?.checkPermission(requireContext())
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

            findPreference<NotificationPermissionPreference>(getText(R.string.notification_permission_key))?.let { preference ->
                val helper = model.notificationPermissionHelper
                val launcher = notificationPermissionLauncher
                if (helper != null && launcher != null) {
                    helper.permissionGranted.onEach {
                        preference.isVisible = !it
                    }.launchIn(lifecycleScope)
                    preference.onButtonClicked = {
                        helper.requestPermission(this, launcher, requireParentFragment())
                    }
                } else {
                    preference.isVisible = false
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
            applyNavigationBarBottomInset()
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

class PreferenceFragmentViewModel(application: Application) : AndroidViewModel(application) {
    val notificationPermissionHelper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimePermissionHelper(
            Manifest.permission.POST_NOTIFICATIONS,
            R.string.notification_permission_rationale
        )
    } else {
        null
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

class NotificationPermissionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = androidx.preference.R.style.Preference
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    var onButtonClicked: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.apply {
            isClickable = false
            isFocusable = false
        }
        holder.findViewById(R.id.button).setOnClickListener { onButtonClicked?.invoke() }
    }
}

class SettingsAppColorsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    @StyleRes defStyleRes: Int = androidx.preference.R.style.Preference
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {
    init {
        // Set initial value so that height will remain fixed
        summary = "\u200B"
    }

    private lateinit var scope: CoroutineScope
    override fun onAttached() {
        super.onAttached()
        scope = MainScope()
        scope.launch {
            Settings.colorTheme.flow().collect {
                setSummary(when (it) {
                    Settings.ColorTheme.System -> R.string.prefs_color_theme_system
                    Settings.ColorTheme.Red -> R.string.prefs_color_theme_red
                    Settings.ColorTheme.Teal -> R.string.prefs_color_theme_teal
                })
            }
        }
    }

    override fun onDetached() {
        super.onDetached()
        scope.cancel()
    }
}

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

        val choiceList = checkNotNull(view.findViewById<LinearLayout>(R.id.choice_list))
        for (theme in Settings.ColorTheme.values()) {
            if (theme == Settings.ColorTheme.System && !DynamicColors.isDynamicColorAvailable()) {
                continue
            }
            SettingsColorThemeChoiceView(requireContext()).apply {
                setText(when (theme) {
                    Settings.ColorTheme.System -> R.string.prefs_color_theme_system
                    Settings.ColorTheme.Red -> R.string.prefs_color_theme_red
                    Settings.ColorTheme.Teal -> R.string.prefs_color_theme_teal
                })
                if (theme != Settings.ColorTheme.System) {
                    setColorFromTheme(theme.activityThemeResId)
                }
                choiceList.addView(
                    this,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                viewsToTheme[this] = theme
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

class SettingsColorThemeChoiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = R.attr.settingsColorThemeChoiceViewStyle
) : AppCompatCheckedTextView(context, attrs, defStyleAttr) {
    fun setColorFromTheme(@StyleRes activityThemeResId: Int) {
        val color = MaterialColors.getColor(
            ContextThemeWrapper(context, activityThemeResId),
            androidx.appcompat.R.attr.colorPrimary,
            SettingsColorThemeChoiceView::class.simpleName
        )
        val colorDrawable = AppCompatResources.getDrawable(context, R.drawable.settings_color_theme_color_view_shape_48dp)?.apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.ADD)
        }
        updateCompoundDrawables(end = colorDrawable)
    }
}
