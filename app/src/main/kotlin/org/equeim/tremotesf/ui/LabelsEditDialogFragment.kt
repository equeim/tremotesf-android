// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.StateFlow
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.performRecoveringRequest
import org.equeim.tremotesf.rpc.requests.getTorrentsLabels
import org.equeim.tremotesf.rpc.requests.torrentproperties.setTorrentsLabels
import org.equeim.tremotesf.rpc.stateIn
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogContent
import org.equeim.tremotesf.ui.components.TremotesfLabelsEditor
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester
import org.equeim.tremotesf.ui.navigateToDetailedErrorDialog
import org.equeim.tremotesf.ui.utils.SnapshotStateListSaver

class LabelsEditDialogFragment : ComposeDialogFragment() {
    @Composable
    override fun Content(navController: NavController) {
        val args = LabelsEditDialogFragmentArgs.fromBundle(requireArguments())
        val model = viewModel<LabelsEditDialogViewModel> { LabelsEditDialogViewModel(args.torrentHashStrings.asList()) }
        LabelsEditDialogContent(
            initialEnabledLabels = args::enabledLabels,
            allLabels = model.allLabels.collectAsStateWithLifecycle(),
            updateLabels = model::updateLabels,
            onDismissRequest = ::dismiss,
            navigateToDetailedErrorDialog = navController::navigateToDetailedErrorDialog
        )
    }
}

@Composable
private fun LabelsEditDialogContent(
    initialEnabledLabels: () -> Array<String>,
    allLabels: State<RpcRequestState<Set<String>>>,
    updateLabels: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit
) {
    val enabledLabels: SnapshotStateList<String> = rememberSaveable(saver = SnapshotStateListSaver()) {
        SnapshotStateList<String>().apply {
            addAll(initialEnabledLabels().sortedWith(AlphanumericComparator()))
        }
    }

    TremotesfAlertDialogContent(
        title = { Text(stringResource(R.string.edit_labels)) },
        text = {
            TremotesfScreenContentWithPlaceholder(
                requestState = allLabels.value,
                onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
                placeholdersModifier = Modifier.fillMaxWidth()
            ) { allLabels ->
                val allLabelsSorted = remember(LocalConfiguration.current.locales) {
                    mutableStateOf(allLabels.sortedWith(AlphanumericComparator()))
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
    val allLabels: StateFlow<RpcRequestState<Set<String>>> = GlobalRpcClient.performRecoveringRequest {
        getTorrentsLabels()
    }.stateIn(GlobalRpcClient, viewModelScope)

    fun updateLabels(labels: List<String>) {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_labels_error) {
            setTorrentsLabels(torrentHashStrings, labels)
        }
    }
}
