// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.Destination
import org.equeim.tremotesf.ui.NavController
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar

@Parcelize
data object ServerSettingsDestination : Destination {
    @Composable
    override fun Content(navController: NavController) {
        ServerSettingsScreen(
            navigateUp = navController::popBackStack,
            navigateTo = navController::navigateTo
        )
    }
}

@Composable
private fun ServerSettingsScreen(navigateUp: () -> Unit, navigateTo: (Destination) -> Unit) {
    Scaffold(
        topBar = {
            TremotesfTopAppBar(
                title = stringResource(R.string.server_settings),
                navigateUp = navigateUp,
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
        ) {
            PageListItem(R.string.server_settings_downloading) { navigateTo(ServerSettingsDownloadingDestination) }
            PageListItem(R.string.server_settings_seeding) { navigateTo(ServerSettingsSeedingDestination) }
            PageListItem(R.string.server_settings_queue) { navigateTo(ServerSettingsQueueDestination) }
            PageListItem(R.string.server_settings_speed) { navigateTo(ServerSettingsSpeedDestination) }
            PageListItem(R.string.server_settings_network) { navigateTo(ServerSettingsNetworkDestination) }
        }
    }
}

@Composable
private fun PageListItem(@StringRes title: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(stringResource(title))
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Preview
@Composable
private fun ServerSettingsScreenPreview() = ScreenPreview {
    ServerSettingsScreen(
        navigateUp = {},
        navigateTo = {}
    )
}
