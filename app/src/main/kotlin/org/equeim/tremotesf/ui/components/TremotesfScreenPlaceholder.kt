// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.equeim.tremotesf.R
import org.equeim.tremotesf.rpc.DetailedRpcRequestError
import org.equeim.tremotesf.rpc.GlobalRpcClient
import org.equeim.tremotesf.rpc.RpcRequestError
import org.equeim.tremotesf.rpc.RpcRequestState
import org.equeim.tremotesf.rpc.createRpcRequestErrorForComposePreview
import org.equeim.tremotesf.rpc.getErrorString
import org.equeim.tremotesf.rpc.makeDetailedError
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.DetailedConnectionErrorDialog
import org.equeim.tremotesf.ui.Dimens

@Composable
fun TremotesfPlaceholderText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = LocalContentColor.current.copy(alpha = PLACEHOLDER_TEXT_ALPHA),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun TremotesfLoadingPlaceholder(modifier: Modifier = Modifier, @StringRes text: Int = DEFAULT_LOADING_TEXT) {
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator()
            TremotesfPlaceholderText(stringResource(text))
        }
    }
}

@Preview
@Composable
private fun TremotesfLoadingPlaceholderPreview() = ComponentPreview {
    TremotesfLoadingPlaceholder()
}

@Composable
fun TremotesfErrorPlaceholder(error: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBig),
            modifier = Modifier.align(Alignment.Center)
        ) {
            TremotesfPlaceholderText(error)
        }
    }
}

@Composable
fun TremotesfErrorPlaceholder(
    error: RpcRequestError,
    modifier: Modifier = Modifier,
    onShowDetailedErrorButtonClicked: (DetailedRpcRequestError) -> Unit,
) {
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBig),
            modifier = Modifier.align(Alignment.Center)
        ) {
            TremotesfPlaceholderText(error.getErrorString(LocalContext.current))
            when (error) {
                is RpcRequestError.NoConnectionConfiguration, is RpcRequestError.ConnectionDisabled -> Unit
                else -> {
                    OutlinedButton(onClick = { onShowDetailedErrorButtonClicked(error.makeDetailedError(GlobalRpcClient)) }) {
                        Text(stringResource(R.string.see_detailed_error_message))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TremotesfStringErrorPlaceholderPreview() = ComponentPreview {
    TremotesfErrorPlaceholder(createRpcRequestErrorForComposePreview(), onShowDetailedErrorButtonClicked = {})
}

@Preview
@Composable
private fun TremotesfRpcErrorPlaceholderPreview() = ComponentPreview {
    TremotesfErrorPlaceholder("LOL")
}

@Composable
fun <Response> TremotesfScreenContentWithPlaceholder(
    requestState: RpcRequestState<Response>,
    modifier: Modifier = Modifier,
    placeholdersModifier: Modifier = Modifier,
    @StringRes loadingText: Int = DEFAULT_LOADING_TEXT,
    content: @Composable (response: Response) -> Unit
) {
    var showDetailedErrorDialog: DetailedRpcRequestError? by rememberSaveable { mutableStateOf(null) }

    when (requestState) {
        is RpcRequestState.Loading -> TremotesfLoadingPlaceholder(
            modifier = modifier.then(placeholdersModifier),
            text = loadingText
        )

        is RpcRequestState.Error -> TremotesfErrorPlaceholder(
            error = requestState.error,
            onShowDetailedErrorButtonClicked = { showDetailedErrorDialog = it },
            modifier = modifier.then(placeholdersModifier)
        )

        is RpcRequestState.Loaded -> Box(modifier) {
            content(requestState.response)
        }
    }

    showDetailedErrorDialog?.let {
        DetailedConnectionErrorDialog(error = it, onDismissRequest = { showDetailedErrorDialog = null })
    }
}

private const val PLACEHOLDER_TEXT_ALPHA = 0.6f
private val DEFAULT_LOADING_TEXT = R.string.loading
