// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.DialogSceneStrategy.Companion.DialogKey
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequestIntoStateFlow
import org.equeim.tremotesf.rpc.requests.getTorrentsLabels
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentsLabels
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogContent
import org.equeim.tremotesf.ui.components.TremotesfLabelsEditor
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.utils.SnapshotStateListSaver
import org.equeim.tremotesf.ui.utils.rememberAlphanumericComparator
import kotlinx.parcelize.Parcelize

@Parcelize
data class LabelsEditDialogDestination(
    val torrentHashStrings: List<String>,
    val enabledLabels: List<String>
) : Destination {
    @Composable
    override fun Content(navController: NavController) {
        val model = viewModel { LabelsEditDialogViewModel(torrentHashStrings) }
        LabelsEditDialogContent(
            initialEnabledLabels = { enabledLabels.toTypedArray() },
            allLabels = model.allLabels.collectAsStateWithLifecycle(),
            updateLabels = model::updateLabels,
            onDismissRequest = navController::popBackStack
        )
    }

    override val metadata: Map<String, Any>
        get() = metadata { put(DialogKey, DialogProperties()) }
}

@Composable
private fun LabelsEditDialogContent(
    initialEnabledLabels: () -> Array<String>,
    allLabels: State<RpcRequestState<Set<String>>>,
    updateLabels: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val comparator = rememberAlphanumericComparator()
    val enabledLabels: SnapshotStateList<String> = rememberSaveable(saver = SnapshotStateListSaver()) {
        SnapshotStateList<String>().apply {
            addAll(initialEnabledLabels().sortedWith(comparator))
        }
    }

    TremotesfAlertDialogContent(
        title = { Text(stringResource(R.string.edit_labels)) },
        text = {
            TremotesfScreenContentWithPlaceholder(
                requestState = allLabels.value,
                placeholdersModifier = Modifier.fillMaxWidth(),
                content = { allLabels ->
                    val allLabelsSorted = remember(allLabels, comparator) {
                        mutableStateOf(allLabels.sortedWith(comparator))
                    }
                    val focusRequester = rememberTremotesfInitialFocusRequester()
                    TremotesfLabelsEditor(
                        enabledLabels = enabledLabels,
                        removeLabel = enabledLabels::remove,
                        addLabel = enabledLabels::add,
                        allLabels = allLabelsSorted::value,
                        textFieldFocusRequester = focusRequester
                    )
                }
            )
        },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
            if (allLabels.value is RpcRequestState.Loaded) {
                TextButton(onClick = {
                    updateLabels(enabledLabels)
                    onDismissRequest()
                }) { Text(stringResource(android.R.string.ok)) }
            }
        }
    )
}

class LabelsEditDialogViewModel(private val torrentHashStrings: List<String>) : ViewModel() {
    val allLabels: StateFlow<RpcRequestState<Set<String>>> =
        GlobalRpcClient.performRecoveringRequestIntoStateFlow(viewModelScope) { getTorrentsLabels() }

    fun updateLabels(labels: List<String>) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_labels_error) {
            setTorrentsLabels(torrentHashStrings, labels)
        }
    }
}
