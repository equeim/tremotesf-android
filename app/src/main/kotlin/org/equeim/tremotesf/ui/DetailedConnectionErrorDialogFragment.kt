// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.causes
import org.equeim.tremotesf.rpc.DetailedRpcRequestError
import org.equeim.tremotesf.rpc.redactHeader
import org.equeim.tremotesf.ui.components.DialogPadding
import org.equeim.tremotesf.ui.components.TremotesfAlertDialogWithoutTextPadding
import org.equeim.tremotesf.ui.components.TremotesfIconButtonWithTooltip
import org.equeim.tremotesf.ui.utils.Utils

@Composable
fun DetailedConnectionErrorDialog(error: DetailedRpcRequestError, onDismissRequest: () -> Unit) {
    var showExpandedDetails: ExpandedDetails? by rememberSaveable { mutableStateOf(null) }

    TremotesfAlertDialogWithoutTextPadding(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingBig)
            ) {
                if (showExpandedDetails != null) {
                    TremotesfIconButtonWithTooltip(Icons.AutoMirrored.Filled.ArrowBack, R.string.navigate_up) {
                        showExpandedDetails = null
                    }
                }
                Text(showExpandedDetails?.title ?: stringResource(R.string.detailed_error_message))
            }

        },
        text = {
            showExpandedDetails.let { expandedDetails ->
                if (expandedDetails == null) {
                    MainView(error) { showExpandedDetails = it }
                } else {
                    ExpandedDetailsView(expandedDetails)
                    BackHandler { showExpandedDetails = null }
                }
            }
        },
        buttons = {
            val context = LocalContext.current
            TextButton(onClick = {
                Utils.shareText(
                    error.makeShareString(),
                    context.getText(R.string.share),
                    context
                )
            }) {
                Text(stringResource(R.string.share))
            }
            Spacer(modifier = Modifier.Companion.weight(1.0f))
            TextButton(onDismissRequest) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun MainView(error: DetailedRpcRequestError, showExpandedDetails: (ExpandedDetails) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        DetailsItem("Error: ${error.error}") {
            showExpandedDetails(
                ExpandedDetails(
                    title = "Error",
                    text = error.error.details()
                )
            )
        }
        error.suppressedErrors.forEach {
            HorizontalDivider()
            DetailsItem("Suppressed: error: $it") {
                showExpandedDetails(
                    ExpandedDetails(
                        title = "Suppressed error",
                        text = it.details()
                    )
                )
            }
        }
        error.responseInfo?.let { response ->
            HorizontalDivider()
            DetailsItem("HTTP response: ${response.status}") {
                showExpandedDetails(
                    ExpandedDetails(
                        title = "HTTP response",
                        text = response.details()
                    )
                )
            }
        }
        if (error.serverCertificates.isNotEmpty()) {
            HorizontalDivider()
            DetailsItem("Server certificates") {
                showExpandedDetails(
                    ExpandedDetails(
                        title = "Server certificates",
                        text = error.serverCertificates.joinToString("\n"),
                        showMonospaceAndWithoutWrapping = true
                    )
                )
            }
        }
        if (error.clientCertificates.isNotEmpty()) {
            HorizontalDivider()
            DetailsItem("Client certificates") {
                showExpandedDetails(
                    ExpandedDetails(
                        title = "Client certificates",
                        text = error.clientCertificates.joinToString("\n"),
                        showMonospaceAndWithoutWrapping = true
                    )
                )
            }
        }
        if (error.requestHeaders.isNotEmpty()) {
            HorizontalDivider()
            DetailsItem("HTTP request headers") {
                showExpandedDetails(
                    ExpandedDetails(
                        title = "HTTP request headers",
                        text = error.requestHeaders.joinToString("\n") { header ->
                            val (name, value) = header.redactHeader()
                            "$name: $value"
                        }
                    ))
            }
        }
    }
}


@Composable
private fun DetailsItem(summary: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DialogPadding, vertical = Dimens.SpacingSmall)
            .minimumInteractiveComponentSize()
    ) {
        Text(
            text = summary,
            maxLines = 3,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.0f)
        )
        Icon(Icons.Outlined.Info, contentDescription = summary)
    }
}

private fun Throwable.details(): String = buildString {
    append("${this@details}\n")
    for (cause in causes) {
        append("\nCaused by:\n$cause\n")
    }
}

private fun DetailedRpcRequestError.ResponseInfo.details(): String = buildString {
    append("Status: $status\n")
    append("Protocol: $protocol\n")
    tlsHandshakeInfo?.let { handshake ->
        append("TLS version: ${handshake.tlsVersion}\n")
        append("Cipher suite: ${handshake.cipherSuite}\n")
    }
    append("Headers:\n")
    headers.forEach { header ->
        val (name, value) = header.redactHeader()
        append("  $name: $value\n")
    }
}

private fun DetailedRpcRequestError.makeShareString(): String = buildString {
    append("Error:\n")
    append(error.details().indent())
    appendLine()
    suppressedErrors.forEach {
        append("Suppressed error:\n")
        append(it.details().indent())
        appendLine()
    }
    responseInfo?.let {
        append("HTTP response:\n")
        append(it.details().indent())
        appendLine()
    }
    if (serverCertificates.isNotEmpty()) {
        append("Server certificates:\n")
        append(serverCertificates.joinToString("\n").indent())
        appendLine()
    }
    if (clientCertificates.isNotEmpty()) {
        append("Client certificates:\n")
        append(clientCertificates.joinToString("\n").indent())
        appendLine()
    }
    if (requestHeaders.isNotEmpty()) {
        append("HTTP request headers:\n")
        append(requestHeaders.joinToString("\n") { header ->
            val (name, value) = header.redactHeader()
            "$name: $value"
        }.indent())
        appendLine()
    }
}

private fun String.indent(): String =
    lineSequence()
        .map {
            when {
                it.isBlank() -> it
                else -> "  $it"
            }
        }
        .joinToString("\n")

@Composable
private fun ExpandedDetailsView(details: ExpandedDetails) {
    Box(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .run { if (details.showMonospaceAndWithoutWrapping) horizontalScroll(rememberScrollState()) else this }
            .padding(horizontal = DialogPadding)
    ) {
        SelectionContainer {
            Text(
                text = details.text,
                fontFamily = if (details.showMonospaceAndWithoutWrapping) FontFamily.Companion.Monospace else FontFamily.Companion.Default
            )
        }
    }
}

@Parcelize
private data class ExpandedDetails(
    val title: String,
    val text: String,
    val showMonospaceAndWithoutWrapping: Boolean = false,
) : Parcelable
