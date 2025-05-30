// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.connectionsettingsfragment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.ui.ComposeFragment
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.ScreenPreview
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.components.TremotesfMultiSelectionPanel
import org.equeim.tremotesf.ui.components.TremotesfPlaceholderText
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar
import org.equeim.tremotesf.ui.components.rememberTremotesfMultiSelectionState
import org.equeim.tremotesf.ui.components.selectableBackground
import org.equeim.tremotesf.ui.components.tremotesfMultiSelectionClickable
import org.equeim.tremotesf.ui.utils.safeNavigate

class ConnectionSettingsFragment : ComposeFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel<ConnectionSettingsViewModel>()

        val servers = model.servers.collectAsStateWithLifecycle()
        val comparator = remember(LocalConfiguration.current.locales) { AlphanumericComparator() }
        val sortedServers = remember { derivedStateOf { servers.value.sortedWith(comparator) } }

        ConnectionSettingsScreen(
            navigateUp = navController::navigateUp,
            servers = sortedServers,
            currentServer = model.currentServer.collectAsStateWithLifecycle(),
            setCurrentServer = model::setCurrentServer,
            editServer = { navController.safeNavigate(ConnectionSettingsFragmentDirections.toServerEditFragment(it)) },
            removeServers = model::removeServers,
            addServer = { navController.safeNavigate(ConnectionSettingsFragmentDirections.toServerEditFragment(null)) }
        )
    }
}

@Composable
private fun ConnectionSettingsScreen(
    navigateUp: () -> Unit,
    servers: State<List<String>>,
    currentServer: State<String?>,
    setCurrentServer: (String) -> Unit,
    editServer: (String) -> Unit,
    removeServers: (Set<String>) -> Unit,
    addServer: () -> Unit,
) {
    val selectionState = rememberTremotesfMultiSelectionState(servers) { it }
    Scaffold(
        topBar = {
            TremotesfTopAppBar(
                title = stringResource(R.string.connection_settings),
                navigateUp = navigateUp,
            )
        },
        floatingActionButton = {
            if (!selectionState.hasSelection) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.add_server)) },
                    icon = { Icon(Icons.Filled.Add, stringResource(R.string.add_server)) },
                    onClick = { addServer() },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Box {
            val layoutDirection = LocalLayoutDirection.current
            val contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = innerPadding.calculateBottomPadding() + Dimens.PaddingForSelectionPanel
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .selectableGroup(),
                contentPadding = contentPadding
            ) {
                items(items = servers.value, key = { it }) { server ->
                    ListItem(
                        headlineContent = { Text(server) },
                        leadingContent = {
                            if (!selectionState.hasSelection) {
                                RadioButton(
                                    selected = server == currentServer.value,
                                    onClick = { setCurrentServer(server) }
                                )
                            } else {
                                Checkbox(
                                    checked = selectionState.isSelected(server),
                                    onCheckedChange = null,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = selectableBackground(selectionState.isSelected(server))
                        ),
                        modifier = Modifier.tremotesfMultiSelectionClickable(selectionState, server) {
                            editServer(server)
                        }
                    )
                    HorizontalDivider()
                }
            }

            val noServers: Boolean by remember { derivedStateOf { servers.value.isEmpty() } }
            if (noServers) {
                TremotesfPlaceholderText(stringResource(R.string.no_servers), Modifier.align(Alignment.Center))
            }

            var showRemoveDialog: Boolean by rememberSaveable { mutableStateOf(false) }
            TremotesfMultiSelectionPanel(
                state = selectionState,
                selectedItemsString = R.plurals.servers_selected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(innerPadding)
            ) {
                TremotesfIconButtonWithTooltip(
                    Icons.Filled.Delete,
                    R.string.remove
                ) {
                    showRemoveDialog = true
                }
            }
            if (showRemoveDialog) {
                AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            removeServers(selectionState.selectedKeys)
                            showRemoveDialog = false
                        }) { Text(stringResource(R.string.remove)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRemoveDialog = false
                        }) { Text(stringResource(android.R.string.cancel)) }
                    },
                    text = {
                        Text(
                            pluralStringResource(
                                R.plurals.remove_servers_message,
                                selectionState.selectedCount,
                                selectionState.selectedCount
                            )
                        )
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun ConnectionSettingsScreenPreview() = ScreenPreview {
    ConnectionSettingsScreen(
        navigateUp = {},
        servers = remember { mutableStateOf(listOf("Lol", "Nope")) },
        currentServer = remember { mutableStateOf("Lol") },
        setCurrentServer = {},
        editServer = {},
        removeServers = {},
        addServer = {},
    )
}

class ConnectionSettingsViewModel : ViewModel() {
    val servers: StateFlow<List<String>> =
        GlobalServers.servers.map { servers -> servers.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentServer: StateFlow<String?> =
        GlobalServers.currentServer.map { it?.name }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setCurrentServer(serverName: String) {
        GlobalServers.setCurrentServer(serverName)
    }

    fun removeServers(serverNames: Set<String>) {
        GlobalServers.removeServers(serverNames)
    }
}
