// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.serversettingsfragment

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.components.TremotesfDecimalNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfIntegerNumberInputFieldState
import org.equeim.tremotesf.ui.components.TremotesfScreenContentWithPlaceholder
import org.equeim.tremotesf.ui.components.TremotesfTopAppBar

@Composable
fun ServerSettingsCategory(
    @StringRes title: Int,
    settingsRequestState: State<RpcRequestState<Any>>,
    navigateUp: () -> Unit,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit,
    loadedSettingsContent: @Composable ColumnScope.(Dp) -> Unit
) {
    Scaffold(
        topBar = {
            TremotesfTopAppBar(
                title = stringResource(title),
                navigateUp = navigateUp,
            )
        },
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        TremotesfScreenContentWithPlaceholder(
            requestState = settingsRequestState.value,
            onShowDetailedErrorButtonClicked = navigateToDetailedErrorDialog,
            modifier = Modifier.consumeWindowInsets(innerPadding),
            placeholdersModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.screenContentPadding())
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = Dimens.screenContentPaddingVertical()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
            ) {
                loadedSettingsContent(Dimens.screenContentPaddingHorizontal())
            }
        }
    }
}

@Stable
class ServerSettingsProperty<T : Any>(
    defaultValue: T,
    private val performRpcRequest: suspend RpcClient.(T) -> Unit
) {
    // No need to saved it to SavedStateHandle since we are getting initial values from server anyway
    var value by mutableStateOf(defaultValue)
        private set

    fun update(newValue: T) {
        value = newValue
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error) {
            performRpcRequest(newValue)
        }
    }

    fun reset(initialValue: T) {
        value = initialValue
    }
}

fun ServerSettingsBooleanProperty(performRpcRequest: suspend RpcClient.(Boolean) -> Unit): ServerSettingsProperty<Boolean> =
    ServerSettingsProperty(false, performRpcRequest)

fun ServerSettingsStringProperty(performRpcRequest: suspend RpcClient.(String) -> Unit): ServerSettingsProperty<String> =
    ServerSettingsProperty("", performRpcRequest)

fun ServerSettingsIntegerNumberInputFieldState(performRpcRequest: suspend RpcClient.(Long) -> Unit) =
    TremotesfIntegerNumberInputFieldState {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error) { performRpcRequest(it) }
    }

fun ServerSettingsDecimalNumberInputFieldState(
    performRpcRequest: suspend RpcClient.(Double) -> Unit
) =
    TremotesfDecimalNumberInputFieldState {
        GlobalRpcClient.performBackgroundRpcRequest(R.string.set_server_settings_error) { performRpcRequest(it) }
    }
