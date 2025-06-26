// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.components.TremotesfSwitchWithText

@Composable
fun TorrentsRemoveDialog(
    torrentHashStrings: List<String>,
    removeTorrents: (torrentHashStrings: List<String>, deleteFiles: Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    var initialDeleteFiles: Boolean? by rememberSaveable { mutableStateOf(null) }
    if (initialDeleteFiles == null) {
        LaunchedEffect(null) {
            initialDeleteFiles = Settings.deleteFiles.get()
        }
    }
    initialDeleteFiles?.let {
        TorrentsRemoveDialogImpl(
            torrentHashStrings = torrentHashStrings,
            initialDeleteFiles = { it },
            removeTorrents = removeTorrents,
            onDismissRequest = onDismissRequest
        )
    }
}

@Composable
private fun TorrentsRemoveDialogImpl(
    torrentHashStrings: List<String>,
    initialDeleteFiles: () -> Boolean,
    removeTorrents: (torrentHashStrings: List<String>, deleteFiles: Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    var deleteFiles: Boolean by rememberSaveable { mutableStateOf(initialDeleteFiles()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
                Text(
                    if (torrentHashStrings.size == 1) {
                        stringResource(R.string.remove_torrent_message)
                    } else {
                        pluralStringResource(
                            R.plurals.remove_torrents_message,
                            torrentHashStrings.size,
                            torrentHashStrings.size
                        )
                    }
                )

                TremotesfSwitchWithText(
                    checked = deleteFiles,
                    onCheckedChange = { deleteFiles = it },
                    text = R.string.delete_files,
                    horizontalContentPadding = Dimens.SpacingSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                removeTorrents(torrentHashStrings, deleteFiles)
                onDismissRequest()
            }) {
                Text(stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Preview
@Composable
private fun TorrentsRemoveDialogPreview() = ScreenPreview {
    TorrentsRemoveDialogImpl(
        torrentHashStrings = listOf(""),
        initialDeleteFiles = { true },
        removeTorrents = { _, _ -> },
        onDismissRequest = {}
    )
}
