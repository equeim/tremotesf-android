// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.components.rememberTremotesfInitialFocusRequester

@Composable
fun TorrentRenameDialog(
    torrentName: String,
    rename: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var newName: String by rememberSaveable { mutableStateOf(torrentName) }
    val renameIfNotBlankAndDismiss = {
        if (newName.isNotBlank()) {
            rename(newName)
            onDismissRequest()
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            val focusRequester = rememberTremotesfInitialFocusRequester()
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions { renameIfNotBlankAndDismiss() },
                label = { Text(stringResource(R.string.file_name)) },
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = renameIfNotBlankAndDismiss,
                enabled = newName.isNotBlank()
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
