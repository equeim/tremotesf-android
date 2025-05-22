// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.utils.safeNavigate

class ServerSettingsFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        ServerSettingsScreen(
            navigateUp = navController::navigateUp,
            navigate = navController::safeNavigate,
        )
    }
}

@Composable
private fun ServerSettingsScreen(navigateUp: () -> Unit, navigate: (NavDirections) -> Unit) {
    Scaffold(
        topBar = {
            TremotesfTopAppBar(
                title = stringResource(R.string.server_settings),
                navigateUp = navigateUp,
            )
        }
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            PageListItem(R.string.server_settings_downloading) {
                navigate(ServerSettingsFragmentDirections.toDownloadingFragment())
            }
            PageListItem(R.string.server_settings_seeding) {
                navigate(ServerSettingsFragmentDirections.toSeedingFragment())
            }
            PageListItem(R.string.server_settings_queue) {
                navigate(ServerSettingsFragmentDirections.toQueueFragment())
            }
            PageListItem(R.string.server_settings_speed) {
                navigate(ServerSettingsFragmentDirections.toSpeedFragment())
            }
            PageListItem(R.string.server_settings_network) {
                navigate(ServerSettingsFragmentDirections.toNetworkFragment())
            }
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
        navigate = {}
    )
}
