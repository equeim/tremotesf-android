// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.Settings.ColorTheme
import org.equeim.tremotesf.ui.Settings.DarkThemeMode
import org.equeim.tremotesf.ui.SettingsScreenViewModel.SettingsProperty
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelper
import org.equeim.tremotesf.ui.components.TremotesfRuntimePermissionHelperState
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.components.rememberTremotesfRuntimePermissionHelperState

class SettingsFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<SettingsScreenViewModel>()
        val properties: SettingsScreenViewModel.Properties? by model.properties.collectAsStateWithLifecycle()
        properties?.let { SettingsScreen(navController::navigateUp, it) }
    }
}

@Composable
private fun SettingsScreen(navigateUp: () -> Unit, properties: SettingsScreenViewModel.Properties) {
    Scaffold(
        topBar = { TremotesfTopAppBar(stringResource(R.string.settings), navigateUp) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
                .padding(vertical = Dimens.screenContentPaddingVertical()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val horizontalPadding = Dimens.screenContentPaddingHorizontal()

            TremotesfSectionHeader(R.string.appearance, modifier = Modifier.padding(horizontal = horizontalPadding))

            TremotesfComboBox(
                currentItem = properties.darkThemeMode::value,
                updateCurrentItem = properties.darkThemeMode::set,
                items = DarkThemeMode.entries,
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            DarkThemeMode.Auto -> R.string.prefs_dark_theme_mode_auto
                            DarkThemeMode.On -> R.string.prefs_dark_theme_mode_on
                            DarkThemeMode.Off -> R.string.prefs_dark_theme_mode_off
                        }
                    )
                },
                label = R.string.prefs_dark_theme_mode_title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfComboBox(
                currentItem = properties.colorTheme::value,
                updateCurrentItem = properties.colorTheme::set,
                items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ColorTheme.entries
                } else {
                    ColorTheme.entries - ColorTheme.System
                },
                itemDisplayString = {
                    stringResource(
                        when (it) {
                            ColorTheme.System -> R.string.prefs_color_theme_system
                            ColorTheme.Red -> R.string.prefs_color_theme_red
                            ColorTheme.Teal -> R.string.prefs_color_theme_teal
                        }
                    )
                },
                label = R.string.prefs_color_theme_title,
                leadingIcon = {
                    val color = selectColorScheme(properties.darkThemeMode.value, it).primary
                    val radius = with(LocalDensity.current) { 6.dp.toPx() }
                    Canvas(Modifier.size(24.dp)) {
                        drawRoundRect(color = color, cornerRadius = CornerRadius(radius))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfSwitchWithText(
                checked = properties.torrentCompactView.value,
                onCheckedChange = properties.torrentCompactView::set,
                text = R.string.prefs_torrent_compact_view_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.torrentNameMultiline.value,
                onCheckedChange = properties.torrentNameMultiline::set,
                text = R.string.prefs_torrent_name_multiline_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSectionHeader(
                text = R.string.behaviour,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            TremotesfSwitchWithText(
                checked = properties.quickReturn.value,
                onCheckedChange = properties.quickReturn::set,
                text = R.string.quick_return_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            Text(
                stringResource(R.string.quick_return_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )

            TremotesfSectionHeader(
                text = R.string.torrents,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            TremotesfSwitchWithText(
                checked = properties.deleteFiles.value,
                onCheckedChange = properties.deleteFiles::set,
                text = R.string.prefs_delete_files_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.fillTorrentLinkFromClipboard.value,
                onCheckedChange = properties.fillTorrentLinkFromClipboard::set,
                text = R.string.prefs_link_from_clipboard_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.rememberAddTorrentParameters.value,
                onCheckedChange = properties.rememberAddTorrentParameters::set,
                text = R.string.prefs_remember_add_torrent_parameters_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.askForMergingTrackersWhenAddingExistingTorrent.value,
                onCheckedChange = properties.askForMergingTrackersWhenAddingExistingTorrent::set,
                text = R.string.prefs_ask_for_merging_trackers_when_adding_existing_torrent_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.mergeTrackersWhenAddingExistingTorrent.value,
                onCheckedChange = properties.mergeTrackersWhenAddingExistingTorrent::set,
                text = R.string.prefs_merge_trackers_when_adding_existing_torrent_title,
                enabled = !properties.askForMergingTrackersWhenAddingExistingTorrent.value,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSectionHeader(
                text = R.string.notifications,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            val notificationsPermissionHelperState: TremotesfRuntimePermissionHelperState? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationsPermissionHelperState = rememberTremotesfRuntimePermissionHelperState(
                        requiredPermission = Manifest.permission.POST_NOTIFICATIONS,
                        showRationaleBeforeRequesting = true,
                    )
                    TremotesfRuntimePermissionHelper(
                        state = notificationsPermissionHelperState,
                        permissionRationaleText = R.string.notification_permission_rationale
                    )
                    if (!notificationsPermissionHelperState.permissionGranted) {
                        OutlinedButton(
                            onClick = notificationsPermissionHelperState::requestPermission,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = horizontalPadding)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                            ) {
                                Icon(Icons.Filled.Error, contentDescription = null)
                                Text(stringResource(R.string.request_notification_permission))
                            }
                        }
                    }
                    notificationsPermissionHelperState
                } else {
                    null
                }

            TremotesfSwitchWithText(
                checked = properties.notifyOnFinished.value,
                onCheckedChange = {
                    properties.notifyOnFinished.set(it)
                    if (it && notificationsPermissionHelperState?.permissionGranted == false) {
                        notificationsPermissionHelperState.requestPermission()
                    }
                },
                text = R.string.notify_on_finished,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.notifyOnAdded.value,
                onCheckedChange = {
                    properties.notifyOnAdded.set(it)
                    if (it && notificationsPermissionHelperState?.permissionGranted == false) {
                        notificationsPermissionHelperState.requestPermission()
                    }
                },
                text = R.string.notify_on_added,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            val backgroundUpdateIntervalEnabled: Boolean by remember {
                derivedStateOf {
                    (properties.notifyOnFinished.value || properties.notifyOnAdded.value) && !properties.showPersistentNotification.value
                }
            }
            TremotesfComboBox(
                currentItem = properties.backgroundUpdateInterval::value,
                updateCurrentItem = properties.backgroundUpdateInterval::set,
                items = backgroundUpdateIntervalValues(),
                itemDisplayString = { backgroundUpdateIntervalDisplayString(it) },
                label = R.string.prefs_background_update_interval_title,
                enabled = backgroundUpdateIntervalEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
            )

            TremotesfSectionHeader(
                text = R.string.persistent_notification,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            var showPersistentNotificationWarning: Boolean by rememberSaveable { mutableStateOf(false) }
            if (showPersistentNotificationWarning) {
                AlertDialog(
                    onDismissRequest = { showPersistentNotificationWarning = false },
                    text = { Text(stringResource(R.string.persistent_notification_warning)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showPersistentNotificationWarning = false
                            properties.showPersistentNotification.set(true)
                            if (notificationsPermissionHelperState?.permissionGranted == false) {
                                notificationsPermissionHelperState.requestPermission()
                            }
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPersistentNotificationWarning = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            TremotesfSwitchWithText(
                checked = properties.showPersistentNotification.value,
                onCheckedChange = {
                    if (it) {
                        showPersistentNotificationWarning = true
                    } else {
                        properties.showPersistentNotification.set(false)
                    }
                },
                text = R.string.prefs_persistent_notification_title,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSectionHeader(
                text = R.string.notifications_on_connect,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = Dimens.SpacingSmall)
            )

            TremotesfSwitchWithText(
                checked = properties.notifyOnFinishedSinceLastConnection.value,
                onCheckedChange = {
                    properties.notifyOnFinishedSinceLastConnection.set(it)
                    if (it && notificationsPermissionHelperState?.permissionGranted == false) {
                        notificationsPermissionHelperState.requestPermission()
                    }
                },
                text = R.string.notify_on_finished_since_last,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )

            TremotesfSwitchWithText(
                checked = properties.notifyOnAddedSinceLastConnection.value,
                onCheckedChange = {
                    properties.notifyOnAddedSinceLastConnection.set(it)
                    if (it && notificationsPermissionHelperState?.permissionGranted == false) {
                        notificationsPermissionHelperState.requestPermission()
                    }
                },
                text = R.string.notify_on_added_since_last,
                modifier = Modifier.fillMaxWidth(),
                horizontalContentPadding = horizontalPadding
            )
        }
    }
}

@Composable
private fun backgroundUpdateIntervalValues(): List<Long> {
    val stringValues = stringArrayResource(R.array.prefs_background_update_interval_values)
    return remember(stringValues) { stringValues.map { it.toLong() } }
}

@Composable
private fun backgroundUpdateIntervalDisplayString(value: Long): String {
    val strings = stringArrayResource(R.array.prefs_background_update_interval_entries)
    val index = backgroundUpdateIntervalValues().indexOf(value)
    return if (index != -1) strings[index] else ""
}

class SettingsScreenViewModel : ViewModel() {
    val properties: StateFlow<Properties?> =
        flow { emit(loadProperties()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun loadProperties() = Properties(
        darkThemeMode = SettingsProperty(Settings.darkThemeMode),
        colorTheme = SettingsProperty(Settings.colorTheme),
        torrentCompactView = SettingsProperty(Settings.torrentCompactView),
        torrentNameMultiline = SettingsProperty(Settings.torrentNameMultiline),
        quickReturn = SettingsProperty(Settings.quickReturn),
        deleteFiles = SettingsProperty(Settings.deleteFiles),
        fillTorrentLinkFromClipboard = SettingsProperty(Settings.fillTorrentLinkFromClipboard),
        rememberAddTorrentParameters = SettingsProperty(Settings.rememberAddTorrentParameters),
        askForMergingTrackersWhenAddingExistingTorrent = SettingsProperty(Settings.askForMergingTrackersWhenAddingExistingTorrent),
        mergeTrackersWhenAddingExistingTorrent = SettingsProperty(Settings.mergeTrackersWhenAddingExistingTorrent),
        notifyOnFinished = SettingsProperty(Settings.notifyOnFinished),
        notifyOnAdded = SettingsProperty(Settings.notifyOnAdded),
        backgroundUpdateInterval = SettingsProperty(Settings.backgroundUpdateInterval),
        showPersistentNotification = SettingsProperty(Settings.showPersistentNotification),
        notifyOnFinishedSinceLastConnection = SettingsProperty(Settings.notifyOnFinishedSinceLastConnection),
        notifyOnAddedSinceLastConnection = SettingsProperty(Settings.notifyOnAddedSinceLastConnection),
    )

    @Stable
    class Properties(
        val darkThemeMode: SettingsProperty<DarkThemeMode>,
        val colorTheme: SettingsProperty<ColorTheme>,
        val torrentCompactView: SettingsProperty<Boolean>,
        val torrentNameMultiline: SettingsProperty<Boolean>,
        val quickReturn: SettingsProperty<Boolean>,
        val deleteFiles: SettingsProperty<Boolean>,
        val fillTorrentLinkFromClipboard: SettingsProperty<Boolean>,
        val rememberAddTorrentParameters: SettingsProperty<Boolean>,
        val askForMergingTrackersWhenAddingExistingTorrent: SettingsProperty<Boolean>,
        val mergeTrackersWhenAddingExistingTorrent: SettingsProperty<Boolean>,
        val notifyOnFinished: SettingsProperty<Boolean>,
        val notifyOnAdded: SettingsProperty<Boolean>,
        val backgroundUpdateInterval: SettingsProperty<Long>,
        val showPersistentNotification: SettingsProperty<Boolean>,
        val notifyOnFinishedSinceLastConnection: SettingsProperty<Boolean>,
        val notifyOnAddedSinceLastConnection: SettingsProperty<Boolean>,
    )

    @Stable
    class SettingsProperty<T : Any>(
        private val state: MutableState<T>,
        private val setValue: suspend (T) -> Unit,
    ) {
        val value: T by state

        // For preview only
        constructor(value: T) : this(mutableStateOf(value), {})

        fun set(value: T) {
            state.value = value
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch { setValue(value) }
        }
    }

    private suspend fun <T : Any> SettingsProperty(property: Settings.Property<T>): SettingsProperty<T> {
        val channel = property.flow().produceIn(viewModelScope)
        val state = mutableStateOf(channel.receive())
        viewModelScope.launch { channel.consumeEach(state::value::set) }
        return SettingsProperty(
            state = state,
            setValue = property::set
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() = ScreenPreview {
    SettingsScreen(
        navigateUp = {},
        properties = SettingsScreenViewModel.Properties(
            darkThemeMode = SettingsProperty(DarkThemeMode.On),
            colorTheme = SettingsProperty(ColorTheme.Red),
            torrentCompactView = SettingsProperty(false),
            torrentNameMultiline = SettingsProperty(true),
            quickReturn = SettingsProperty(false),
            deleteFiles = SettingsProperty(true),
            fillTorrentLinkFromClipboard = SettingsProperty(false),
            rememberAddTorrentParameters = SettingsProperty(true),
            askForMergingTrackersWhenAddingExistingTorrent = SettingsProperty(false),
            mergeTrackersWhenAddingExistingTorrent = SettingsProperty(true),
            notifyOnFinished = SettingsProperty(false),
            notifyOnAdded = SettingsProperty(true),
            backgroundUpdateInterval = SettingsProperty(15),
            showPersistentNotification = SettingsProperty(false),
            notifyOnFinishedSinceLastConnection = SettingsProperty(true),
            notifyOnAddedSinceLastConnection = SettingsProperty(false),
        )
    )
}
