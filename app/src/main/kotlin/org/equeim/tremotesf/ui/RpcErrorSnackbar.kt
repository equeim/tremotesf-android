// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.GlobalRpcClient.BackgroundRpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.getErrorString

@Composable
fun ShowRpcErrorsSnackbar(
    snackbarHostState: SnackbarHostState,
    backgroundRpcErrors: ReceiveChannel<BackgroundRpcRequestError>,
    navigateToDetailedErrorDialog: (RpcRequestError) -> Unit
) {
    val displayedError = rememberSaveable { mutableStateOf<BackgroundRpcRequestError?>(null) }
    val snackbarShownEvents = remember { Channel<Unit>() }

    LaunchedEffect(snackbarHostState, backgroundRpcErrors, displayedError) {
        if (displayedError.value != null) {
            snackbarShownEvents.receive()
            displayedError.value = null
        }
        for (error in backgroundRpcErrors) {
            displayedError.value = error
            snackbarShownEvents.receive()
            displayedError.value = null
        }
    }

    displayedError.value?.let { error ->
        val message = stringResource(error.errorContext, error.error.getErrorString())
        val actionLabel = stringResource(R.string.see_detailed_error_message)
        LaunchedEffect(error, message) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                navigateToDetailedErrorDialog(error.error)
            }
            snackbarShownEvents.send(Unit)
        }
    }
}
