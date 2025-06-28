// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.TransferRate
import org.equeim.tremotesf.rpc.requests.serversettings.SpeedServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.SpeedServerSettings.AlternativeLimitsDays
import org.equeim.tremotesf.rpc.requests.serversettings.getSpeedServerSettings
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsBeginTime
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsDays
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsEnabled
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsEndTime
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeLimitsScheduled
import org.equeim.tremotesf.rpc.requests.serversettings.setAlternativeUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setDownloadSpeedLimited
import org.equeim.tremotesf.rpc.requests.serversettings.setUploadSpeedLimit
import org.equeim.tremotesf.rpc.requests.serversettings.setUploadSpeedLimited
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.applyDisabledAlpha
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfNumberInputField
import org.equeim.tremotesf.ui.components.TremotesfSectionHeader
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText
import org.equeim.tremotesf.ui.components.rememberTremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

class SpeedFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<SpeedFragmentViewModel>()
        ServerSettingsSpeedScreen(
            settingsRequestState = model.settings.collectAsStateWithLifecycle(),
            navigateUp = navController::navigateUp,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog,
            downloadSpeedLimited = model.downloadSpeedLimited,
            downloadSpeedLimit = model.downloadSpeedLimit,
            uploadSpeedLimited = model.uploadSpeedLimited,
            uploadSpeedLimit = model.uploadSpeedLimit,
            alternativeLimitsEnabled = model.alternativeLimitsEnabled,
            alternativeDownloadSpeedLimit = model.alternativeDownloadSpeedLimit,
            alternativeUploadSpeedLimit = model.alternativeUploadSpeedLimit,
            alternativeLimitsScheduled = model.alternativeLimitsScheduled,
            alternativeLimitsBeginTime = model.alternativeLimitsBeginTime,
            alternativeLimitsEndTime = model.alternativeLimitsEndTime,
            alternativeLimitsDays = model.alternativeLimitsDays,
            backgroundRpcRequestsErrors = GlobalRpcClient.backgroundRpcRequestsErrors
        )
    }
}

@Composable
private fun ServerSettingsSpeedScreen(
    settingsRequestState: State<RpcRequestState<Any>>,
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    downloadSpeedLimited: ServerSettingsProperty<Boolean>,
    downloadSpeedLimit: TremotesfIntegerNumberInputFieldState,
    uploadSpeedLimited: ServerSettingsProperty<Boolean>,
    uploadSpeedLimit: TremotesfIntegerNumberInputFieldState,
    alternativeLimitsEnabled: ServerSettingsProperty<Boolean>,
    alternativeDownloadSpeedLimit: TremotesfIntegerNumberInputFieldState,
    alternativeUploadSpeedLimit: TremotesfIntegerNumberInputFieldState,
    alternativeLimitsScheduled: ServerSettingsProperty<Boolean>,
    alternativeLimitsBeginTime: ServerSettingsProperty<LocalTime>,
    alternativeLimitsEndTime: ServerSettingsProperty<LocalTime>,
    alternativeLimitsDays: ServerSettingsProperty<AlternativeLimitsDays>,
    backgroundRpcRequestsErrors: ReceiveChannel<GlobalRpcClient.BackgroundRpcRequestError>
) {
    ServerSettingsCategory(
        title = R.string.server_settings_speed,
        settingsRequestState = settingsRequestState,
        navigateUp = navigateUp,
        navigateToDetailedErrorDialog = navigateToDetailedErrorDialog,
        backgroundRpcRequestsErrors = backgroundRpcRequestsErrors
    ) { horizontalPadding ->
        TremotesfSectionHeader(R.string.limits, modifier = Modifier.padding(horizontal = horizontalPadding))
        TremotesfSwitchWithText(
            checked = downloadSpeedLimited.value,
            text = R.string.download_noun,
            onCheckedChange = downloadSpeedLimited::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = downloadSpeedLimit,
            range = SPEED_LIMIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            enabled = downloadSpeedLimited.value,
            suffix = R.string.text_field_suffix_kbps
        )
        TremotesfSwitchWithText(
            checked = uploadSpeedLimited.value,
            text = R.string.upload_noun,
            onCheckedChange = uploadSpeedLimited::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = uploadSpeedLimit,
            range = SPEED_LIMIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            enabled = uploadSpeedLimited.value,
            suffix = R.string.text_field_suffix_kbps
        )
        TremotesfSectionHeader(
            R.string.alternative_limits,
            Modifier
                .padding(top = Dimens.SpacingSmall)
                .padding(horizontal = horizontalPadding)
        )
        TremotesfSwitchWithText(
            checked = alternativeLimitsEnabled.value,
            text = R.string.enable,
            onCheckedChange = alternativeLimitsEnabled::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        TremotesfNumberInputField(
            state = alternativeDownloadSpeedLimit,
            range = SPEED_LIMIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            enabled = alternativeLimitsEnabled.value,
            suffix = R.string.text_field_suffix_kbps,
            label = R.string.download_noun
        )
        TremotesfNumberInputField(
            state = alternativeUploadSpeedLimit,
            range = SPEED_LIMIT_RANGE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            enabled = alternativeLimitsEnabled.value,
            suffix = R.string.text_field_suffix_kbps,
            label = R.string.upload_noun
        )
        TremotesfSwitchWithText(
            checked = alternativeLimitsScheduled.value,
            text = R.string.scheduled,
            onCheckedChange = alternativeLimitsScheduled::update,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )
        LimitsScheduleTime(
            label = R.string.from,
            time = alternativeLimitsBeginTime,
            enabled = alternativeLimitsScheduled.value,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPading = horizontalPadding
        )
        LimitsScheduleTime(
            label = R.string.to,
            time = alternativeLimitsEndTime,
            enabled = alternativeLimitsScheduled.value,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPading = horizontalPadding
        )

        val context = LocalContext.current
        val localeList = LocalConfiguration.current.locales
        val dayNames: Map<AlternativeLimitsDays, String> = remember(localeList) {
            buildMap {
                put(AlternativeLimitsDays.All, context.getString(R.string.every_day))
                put(AlternativeLimitsDays.Weekdays, context.getString(R.string.weekdays))
                put(AlternativeLimitsDays.Weekends, context.getString(R.string.weekends))
                val locale = Locale.getDefault()
                for (dayOfWeek in DayOfWeek.entries) {
                    put(
                        dayOfWeek.toAlternativeSpeedLimitsDays(),
                        dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                    )
                }
            }
        }
        val dropdownDays: List<AlternativeLimitsDays> = remember(localeList) {
            buildList {
                add(AlternativeLimitsDays.All)
                add(AlternativeLimitsDays.Weekdays)
                add(AlternativeLimitsDays.Weekends)
                val firstDayOfWeek =
                    DayOfWeek.of(WeekFields.of(Locale.getDefault()).firstDayOfWeek.value)
                val daysOfWeek = generateSequence(firstDayOfWeek) { it + 1 }.take(DayOfWeek.entries.size)
                for (day in daysOfWeek) {
                    add(day.toAlternativeSpeedLimitsDays())
                }
            }
        }

        TremotesfComboBox(
            currentItem = alternativeLimitsDays::value,
            updateCurrentItem = alternativeLimitsDays::update,
            items = dropdownDays,
            itemDisplayString = { checkNotNull(dayNames[it]) },
            enabled = alternativeLimitsScheduled.value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
        )
    }
}

private val SPEED_LIMIT_RANGE: LongRange = 0..(TransferRate.MAX_VALUE).kiloBytesPerSecond

private fun DayOfWeek.toAlternativeSpeedLimitsDays(): AlternativeLimitsDays = when (this) {
    DayOfWeek.SUNDAY -> AlternativeLimitsDays.Sunday
    DayOfWeek.MONDAY -> AlternativeLimitsDays.Monday
    DayOfWeek.TUESDAY -> AlternativeLimitsDays.Tuesday
    DayOfWeek.WEDNESDAY -> AlternativeLimitsDays.Wednesday
    DayOfWeek.THURSDAY -> AlternativeLimitsDays.Thursday
    DayOfWeek.FRIDAY -> AlternativeLimitsDays.Friday
    DayOfWeek.SATURDAY -> AlternativeLimitsDays.Saturday
}

@Preview
@Composable
private fun ServerSettingsSpeedScreenPreview() = ScreenPreview {
    ServerSettingsSpeedScreen(
        settingsRequestState = remember { mutableStateOf(RpcRequestState.Loaded(Unit)) },
        navigateUp = {},
        navigateToDetailedErrorDialog = {},
        downloadSpeedLimited = remember { ServerSettingsBooleanProperty {} },
        downloadSpeedLimit = rememberTremotesfIntegerNumberInputFieldState(),
        uploadSpeedLimited = remember { ServerSettingsBooleanProperty {} },
        uploadSpeedLimit = rememberTremotesfIntegerNumberInputFieldState(),
        alternativeLimitsEnabled = remember { ServerSettingsBooleanProperty {} },
        alternativeDownloadSpeedLimit = rememberTremotesfIntegerNumberInputFieldState(),
        alternativeUploadSpeedLimit = rememberTremotesfIntegerNumberInputFieldState(),
        alternativeLimitsScheduled = remember { ServerSettingsBooleanProperty {} },
        alternativeLimitsBeginTime = remember { ServerSettingsProperty(LocalTime.MIDNIGHT) {} },
        alternativeLimitsEndTime = remember { ServerSettingsProperty(LocalTime.NOON) {} },
        alternativeLimitsDays = remember { ServerSettingsProperty(AlternativeLimitsDays.All) {} },
        backgroundRpcRequestsErrors = remember { Channel() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitsScheduleTime(
    @StringRes label: Int,
    time: ServerSettingsProperty<LocalTime>,
    modifier: Modifier = Modifier,
    horizontalContentPading: Dp = Dp.Unspecified,
    enabled: Boolean = true,
) {
    var showTimePickerDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clickable(enabled) { showTimePickerDialog = true }
            .padding(vertical = Dimens.SpacingSmall)
            .padding(horizontal = horizontalContentPading),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.applyDisabledAlpha(enabled),
        )
        val localeList = LocalConfiguration.current.locales
        val formatter = remember(localeList) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
        Text(
            text = formatter.format(time.value),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.applyDisabledAlpha(enabled))
    }
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.value.hour,
            initialMinute = time.value.minute,
        )
        var showTimeInput: Boolean by rememberSaveable { mutableStateOf(false) }
        val displayMode: TimePickerDisplayMode by remember {
            derivedStateOf { if (showTimeInput) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker }
        }
        TimePickerDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    time.update(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showTimePickerDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { TimePickerDialogDefaults.Title(displayMode) },
            modeToggleButton = {
                TimePickerDialogDefaults.DisplayModeToggle(
                    onDisplayModeChange = { showTimeInput = !showTimeInput },
                    displayMode = displayMode
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePickerDialog = false
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            if (displayMode == TimePickerDisplayMode.Picker) {
                TimePicker(timePickerState)
            } else {
                TimeInput(timePickerState)
            }
        }
    }
}

@Preview
@Composable
private fun LimitsScheduleTimePreview() = ComponentPreview {
    LimitsScheduleTime(label = R.string.from, time = remember { ServerSettingsProperty(LocalTime.NOON) {} })
}

class SpeedFragmentViewModel(application: Application) : AndroidViewModel(application) {
    val settings: StateFlow<RpcRequestState<Any>> =
        GlobalRpcClient.performRecoveringRequest { getSpeedServerSettings() }
            .onEach { if (it is RpcRequestState.Loaded) setInitialState(it.response) }
            .stateIn(GlobalRpcClient, viewModelScope)

    val downloadSpeedLimited: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setDownloadSpeedLimited)

    val downloadSpeedLimit: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState { setDownloadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it)) }

    val uploadSpeedLimited: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setUploadSpeedLimited)

    val uploadSpeedLimit: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState { setUploadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it)) }


    val alternativeLimitsEnabled: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setAlternativeLimitsEnabled)

    val alternativeDownloadSpeedLimit: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState {
            setAlternativeDownloadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it))
        }

    val alternativeUploadSpeedLimit: TremotesfIntegerNumberInputFieldState =
        ServerSettingsIntegerNumberInputFieldState {
            setAlternativeUploadSpeedLimit(TransferRate.fromKiloBytesPerSecond(it))
        }

    val alternativeLimitsScheduled: ServerSettingsProperty<Boolean> =
        ServerSettingsBooleanProperty(RpcClient::setAlternativeLimitsScheduled)

    val alternativeLimitsBeginTime: ServerSettingsProperty<LocalTime> =
        ServerSettingsProperty(LocalTime.MIDNIGHT, RpcClient::setAlternativeLimitsBeginTime)

    val alternativeLimitsEndTime: ServerSettingsProperty<LocalTime> =
        ServerSettingsProperty(LocalTime.MIDNIGHT, RpcClient::setAlternativeLimitsEndTime)

    val alternativeLimitsDays: ServerSettingsProperty<AlternativeLimitsDays> =
        ServerSettingsProperty(AlternativeLimitsDays.All, RpcClient::setAlternativeLimitsDays)

    private fun setInitialState(settings: SpeedServerSettings) {
        downloadSpeedLimited.reset(settings.downloadSpeedLimited)
        downloadSpeedLimit.reset(settings.downloadSpeedLimit.kiloBytesPerSecond)
        uploadSpeedLimited.reset(settings.uploadSpeedLimited)
        uploadSpeedLimit.reset(settings.uploadSpeedLimit.kiloBytesPerSecond)
        alternativeLimitsEnabled.reset(settings.alternativeLimitsEnabled)
        alternativeDownloadSpeedLimit.reset(settings.alternativeDownloadSpeedLimit.kiloBytesPerSecond)
        alternativeUploadSpeedLimit.reset(settings.alternativeUploadSpeedLimit.kiloBytesPerSecond)
        alternativeLimitsScheduled.reset(settings.alternativeLimitsScheduled)
        alternativeLimitsBeginTime.reset(settings.alternativeLimitsBeginTime)
        alternativeLimitsEndTime.reset(settings.alternativeLimitsEndTime)
        alternativeLimitsDays.reset(settings.alternativeLimitsDays)
    }
}
