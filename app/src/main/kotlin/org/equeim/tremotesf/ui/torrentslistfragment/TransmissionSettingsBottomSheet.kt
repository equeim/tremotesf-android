// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.torrentslistfragment

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.applyDisabledAlpha
import org.equeim.tremotesf.ui.components.TremotesfComboBox
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmissionSettingsBottomSheet(
    shouldConnectToServer: State<Boolean>,
    setShouldConnectToServer: (Boolean) -> Unit,
    currentServer: State<String?>,
    setCurrentServer: (String) -> Unit,
    servers: State<List<String>>,
    alternativeSpeedLimitsEnabled: State<Boolean>,
    setAlternativeSpeedLimitsEnabled: (Boolean) -> Unit,
    navigateToConnectionSettingsScreen: () -> Unit,
    navigateToServerSettingsScreen: () -> Unit,
    navigateToServerStatsDialog: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hide: () -> Unit = {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismissRequest()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        TransmissionSettingsBottomSheetContent(
            shouldConnectToServer = shouldConnectToServer,
            setShouldConnectToServer = setShouldConnectToServer,
            currentServer = currentServer,
            setCurrentServer = setCurrentServer,
            servers = servers,
            alternativeSpeedLimitsEnabled = alternativeSpeedLimitsEnabled,
            setAlternativeSpeedLimitsEnabled = setAlternativeSpeedLimitsEnabled,
            navigateToConnectionSettingsScreen = navigateToConnectionSettingsScreen,
            navigateToServerSettingsScreen = navigateToServerSettingsScreen,
            navigateToServerStatsDialog = navigateToServerStatsDialog,
            hide = hide
        )
    }
}

@Composable
private fun TransmissionSettingsBottomSheetContent(
    shouldConnectToServer: State<Boolean>,
    setShouldConnectToServer: (Boolean) -> Unit,
    currentServer: State<String?>,
    setCurrentServer: (String) -> Unit,
    servers: State<List<String>>,
    alternativeSpeedLimitsEnabled: State<Boolean>,
    setAlternativeSpeedLimitsEnabled: (Boolean) -> Unit,
    navigateToConnectionSettingsScreen: () -> Unit,
    navigateToServerSettingsScreen: () -> Unit,
    navigateToServerStatsDialog: () -> Unit,
    hide: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Dimens.screenContentPaddingVertical()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        Text(
            text = stringResource(R.string.transmission_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        OutlinedButton(
            onClick = { setShouldConnectToServer(!shouldConnectToServer.value) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                stringResource(
                    if (shouldConnectToServer.value) {
                        R.string.disconnect
                    } else {
                        R.string.connect
                    }
                )
            )
        }

        val horizontalPadding = Dimens.screenContentPaddingHorizontal()

        val comparator = remember(LocalConfiguration.current.locales) { AlphanumericComparator() }
        val sortedServers = remember { derivedStateOf { servers.value.sortedWith(comparator) } }

        TremotesfComboBox(
            currentItem = { currentServer.value.orEmpty() },
            updateCurrentItem = setCurrentServer,
            items = sortedServers.value,
            itemDisplayString = { it },
            label = R.string.server,
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .fillMaxWidth()
        )

        ClickableText(R.string.connection_settings) {
            navigateToConnectionSettingsScreen()
            hide()
        }

        HorizontalDivider()

        ClickableText(R.string.server_settings, enabled = shouldConnectToServer.value) {
            navigateToServerSettingsScreen()
            hide()
        }

        TremotesfSwitchWithText(
            checked = alternativeSpeedLimitsEnabled.value,
            onCheckedChange = setAlternativeSpeedLimitsEnabled,
            text = R.string.alternative_speed_limits,
            enabled = shouldConnectToServer.value,
            modifier = Modifier.fillMaxWidth(),
            horizontalContentPadding = horizontalPadding
        )

        ClickableText(R.string.server_stats, enabled = shouldConnectToServer.value) {
            navigateToServerStatsDialog()
            hide()
        }
    }
}

@Composable
private fun ClickableText(@StringRes text: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = stringResource(text),
        color = LocalContentColor.current.applyDisabledAlpha(enabled),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = enabled)
            .padding(vertical = 12.dp)
            .padding(horizontal = Dimens.screenContentPaddingHorizontal())
    )
}

@Preview
@Composable
private fun TransmissionSettingsBottomSheetPreview() = ComponentPreview {
    TransmissionSettingsBottomSheetContent(
        shouldConnectToServer = remember { mutableStateOf(true) },
        setShouldConnectToServer = {},
        currentServer = remember { mutableStateOf("localhost") },
        setCurrentServer = {},
        servers = remember { mutableStateOf(emptyList()) },
        alternativeSpeedLimitsEnabled = remember { mutableStateOf(false) },
        setAlternativeSpeedLimitsEnabled = {},
        navigateToConnectionSettingsScreen = {},
        navigateToServerSettingsScreen = {},
        navigateToServerStatsDialog = {},
        hide = {},
    )
}
