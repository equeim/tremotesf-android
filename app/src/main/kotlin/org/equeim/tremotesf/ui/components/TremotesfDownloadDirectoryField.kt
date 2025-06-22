// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.GlobalServers
import org.equeim.tremotesf.rpc.requests.NormalizedRpcPath
import org.equeim.tremotesf.rpc.toNativeSeparators
import org.equeim.tremotesf.ui.History

@Parcelize
data class DownloadDirectoryItem(
    val directory: String,
    val canBeRemoved: Boolean,
) : Parcelable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TremotesfDownloadDirectoryField(
    downloadDirectory: String,
    onDownloadDirectoryChanged: (String) -> Unit,
    allDownloadDirectories: List<DownloadDirectoryItem>,
    removeDownloadDirectory: (DownloadDirectoryItem) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes label: Int = 0,
    imeAction: ImeAction = ImeAction.Unspecified,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    @StringRes supportingText: Int = 0
) {
    var expanded: Boolean by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.width(IntrinsicSize.Min)
    ) {
        OutlinedTextField(
            value = downloadDirectory,
            onValueChange = onDownloadDirectoryChanged,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = imeAction),
            keyboardActions = keyboardActions,
            label = label.takeIf { it != 0 }?.let { { Text(stringResource(label)) } },
            isError = isError,
            supportingText = supportingText.takeIf { it != 0 }?.let { { Text(stringResource(label)) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (item in allDownloadDirectories) {
                DropdownMenuItem(
                    text = { Text(item.directory) },
                    trailingIcon = if (item.canBeRemoved) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, contentDescription = null)
                                TremotesfIconButtonWithTooltip(
                                    icon = Icons.Filled.Delete,
                                    textId = R.string.remove
                                ) { removeDownloadDirectory(item) }
                            }
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onDownloadDirectoryChanged(item.directory)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

fun getInitialAllDownloadDirectories(
    downloadDirectoryFromServerSettings: NormalizedRpcPath,
    torrentsDownloadDirectories: Set<NormalizedRpcPath>,
    comparator: AlphanumericComparator
): List<DownloadDirectoryItem> = getAllDownloadDirectories(
    downloadDirectoryFromServerSettings = downloadDirectoryFromServerSettings,
    torrentsDownloadDirectories = torrentsDownloadDirectories,
    lastDownloadDirectories = GlobalServers.serversState.value.currentServer?.lastDownloadDirectories.orEmpty(),
    comparator = comparator
)

fun updateAllDownloadDirectories(
    restoredAllDownloadDirectories: List<DownloadDirectoryItem>,
    downloadDirectoryFromServerSettings: NormalizedRpcPath,
    torrentsDownloadDirectories: Set<NormalizedRpcPath>,
    comparator: AlphanumericComparator
): List<DownloadDirectoryItem> {
    return getAllDownloadDirectories(
        downloadDirectoryFromServerSettings = downloadDirectoryFromServerSettings,
        torrentsDownloadDirectories = torrentsDownloadDirectories,
        lastDownloadDirectories = restoredAllDownloadDirectories.mapNotNull {
            if (it.canBeRemoved) it.directory else null
        },
        comparator = comparator
    )
}

private fun getAllDownloadDirectories(
    downloadDirectoryFromServerSettings: NormalizedRpcPath,
    torrentsDownloadDirectories: Set<NormalizedRpcPath>,
    lastDownloadDirectories: List<String>,
    comparator: AlphanumericComparator
): List<DownloadDirectoryItem> {
    val directories = sortedMapOf<String, Boolean>(comparator)
    directories.put(downloadDirectoryFromServerSettings.toNativeSeparators(), false)
    torrentsDownloadDirectories.forEach { directories.putIfAbsent(it.toNativeSeparators(), false) }
    lastDownloadDirectories.forEach { directories.putIfAbsent(it, true) }
    return directories.map { (directory, canBeRemoved) -> DownloadDirectoryItem(directory, canBeRemoved) }
}
