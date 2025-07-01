// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.equeim.tremotesf.R
import org.equeim.tremotesf.torrentfile.TorrentFilesTree
import org.equeim.tremotesf.torrentfile.TorrentFilesTree.Item.WantedState
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.FileDownload
import org.equeim.tremotesf.ui.Folder
import org.equeim.tremotesf.ui.InsertDriveFile
import org.equeim.tremotesf.ui.LowPriority

@Composable
fun TremotesfTorrentsFilesList(
    filesTree: TorrentFilesTree,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    itemSupportingContent: @Composable (TorrentFilesTree.Item) -> Unit
) {
    val items: List<TorrentFilesTree.Item> by filesTree.items.collectAsStateWithLifecycle()
    val isAtRoot: Boolean by filesTree.isAtRoot.collectAsStateWithLifecycle()
    val selectionState =
        rememberTremotesfMultiSelectionState(listItems = filesTree.items.collectAsStateWithLifecycle()) { it.nodePath }

    val navigateAndWaitForNewItems: suspend CoroutineScope.(() -> Unit) -> Unit = { navigate ->
        val newItemsEventChannel = Channel<Unit>()
        launch {
            snapshotFlow { items }.drop(1).first()
            newItemsEventChannel.send(Unit)
        }
        navigate()
        newItemsEventChannel.receive()
    }

    val coroutineScope = rememberCoroutineScope()

    val scrollPositionsStack: MutableList<FilesListScrollPosition> = rememberSaveable { mutableListOf() }
    val navigateUp = {
        coroutineScope.launch {
            navigateAndWaitForNewItems { filesTree.navigateUp() }
            val position = scrollPositionsStack.removeLastOrNull()
            if (position != null) {
                listState.requestScrollToItem(position.firstVisibleItemIndex, position.firstVisibleItemScrollOffset)
            }
        }
    }
    val navigateDown = { item: TorrentFilesTree.Item ->
        scrollPositionsStack.add(
            FilesListScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        )
        coroutineScope.launch {
            navigateAndWaitForNewItems { filesTree.navigateDown(item) }
            // For some reason this is necessary even though all items are replaced
            listState.requestScrollToItem(0, 0)
        }
    }

    BackHandler(enabled = !isAtRoot) {
        navigateUp()
    }

    Box {
        val layoutDirection = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            // Account for selection panel
            bottom = contentPadding.calculateBottomPadding() + Dimens.PaddingForSelectionPanel
        )
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            modifier = modifier
        ) {
            if (!isAtRoot) {
                item(key = NavigateUpKey, contentType = ContentType.NavigateUp) {
                    Column(Modifier.animateItem()) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = stringResource(R.string.navigate_up)
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.parent_directory_list_item_label)) },
                            modifier = Modifier.clickable {
                                if (!selectionState.hasSelection) {
                                    navigateUp()
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            items(
                items = items,
                key = { it.nodePath },
                contentType = { ContentType.FileTreeItem }
            ) { item ->
                Column(Modifier.animateItem()) {
                    ListItem(
                        leadingContent = {
                            if (selectionState.isSelected(item.nodePath)) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(R.string.selected),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                if (item.isDirectory) {
                                    Icon(Icons.Filled.Folder, stringResource(R.string.directory_icon))
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, stringResource(R.string.file_icon))
                                }
                            }
                        },
                        headlineContent = { Text(text = item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { itemSupportingContent(item) },
                        trailingContent = {
                            TriStateCheckbox(
                                state = when (item.wantedState) {
                                    WantedState.Wanted -> ToggleableState.On
                                    WantedState.Unwanted -> ToggleableState.Off
                                    WantedState.Mixed -> ToggleableState.Indeterminate
                                },
                                onClick = {
                                    filesTree.setItemsWanted(
                                        nodeIndexes = listOf(item.nodePath.indices.last()),
                                        wanted = when (item.wantedState) {
                                            WantedState.Wanted -> false
                                            WantedState.Unwanted, WantedState.Mixed -> true
                                        }
                                    )
                                },
                                enabled = !selectionState.hasSelection
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = selectableBackground(selectionState.isSelected(item.nodePath))
                        ),
                        modifier = Modifier.tremotesfMultiSelectionClickable(selectionState, item.nodePath) {
                            if (item.isDirectory) {
                                navigateDown(item)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            style = defaultMaterialScrollbarStyle(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(contentPadding)
                .fillMaxHeight()
        )

        SelectionPanel(
            selectionState = selectionState,
            filesTree = filesTree,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
        )
    }
}

@Parcelize
private object NavigateUpKey : Parcelable

private enum class ContentType {
    NavigateUp,
    FileTreeItem
}

@Parcelize
private data class FilesListScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
) : Parcelable

@Composable
private fun SelectionPanel(
    selectionState: TremotesfMultiSelectionState<TorrentFilesTree.Item, TorrentFilesTree.NodePath>,
    filesTree: TorrentFilesTree,
    modifier: Modifier = Modifier
) {
    TremotesfMultiSelectionPanel(
        state = selectionState,
        selectedItemsString = R.plurals.files_selected,
        modifier = modifier
    ) {
        TremotesfIconButtonWithTooltipAndMenu(Icons.Filled.FileDownload, R.string.download_noun) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_verb)) },
                onClick = {
                    filesTree.setItemsWanted(selectionState.getSelectedItemsNodeIndexes(), true)
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.not_download)) },
                onClick = {
                    filesTree.setItemsWanted(selectionState.getSelectedItemsNodeIndexes(), false)
                    dismiss()
                }
            )
        }

        TremotesfIconButtonWithTooltipAndMenu(Icons.Filled.LowPriority, R.string.priority) {
            val selectedItemsPriority: TorrentFilesTree.Item.Priority = remember {
                computeSelectedItemsPriority(selectionState, filesTree)
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.high_priority)) },
                leadingIcon = {
                    RadioButton(
                        selected = (selectedItemsPriority == TorrentFilesTree.Item.Priority.High),
                        onClick = null
                    )
                },
                onClick = {
                    filesTree.setItemsPriority(
                        selectionState.getSelectedItemsNodeIndexes(),
                        TorrentFilesTree.Item.Priority.High
                    )
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.normal_priority)) },
                leadingIcon = {
                    RadioButton(
                        selected = (selectedItemsPriority == TorrentFilesTree.Item.Priority.Normal),
                        onClick = null
                    )
                },
                onClick = {
                    filesTree.setItemsPriority(
                        selectionState.getSelectedItemsNodeIndexes(),
                        TorrentFilesTree.Item.Priority.Normal
                    )
                    dismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.low_pririty)) },
                leadingIcon = {
                    RadioButton(
                        selected = (selectedItemsPriority == TorrentFilesTree.Item.Priority.Low),
                        onClick = null
                    )
                },
                onClick = {
                    filesTree.setItemsPriority(
                        selectionState.getSelectedItemsNodeIndexes(),
                        TorrentFilesTree.Item.Priority.Low
                    )
                    dismiss()
                }
            )
            if (selectedItemsPriority == TorrentFilesTree.Item.Priority.Mixed) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mixed_priority)) },
                    leadingIcon = {
                        RadioButton(selected = true, onClick = null)
                    },
                    onClick = { dismiss() }
                )
            }
        }

        var showRenameDialogForNodePath: TorrentFilesTree.NodePath? by rememberSaveable { mutableStateOf(null) }

        val enableRenameButton: Boolean by remember { derivedStateOf { selectionState.selectedKeys.size == 1 } }
        TremotesfIconButtonWithTooltip(Icons.Filled.Edit, R.string.rename, enabled = enableRenameButton) {
            showRenameDialogForNodePath = selectionState.selectedKeys.firstOrNull()
        }

        showRenameDialogForNodePath?.let { nodePath ->
            var newName: String by rememberSaveable {
                mutableStateOf(filesTree.getCurrentNodeChildByIndexPath(nodePath)?.name.orEmpty())
            }
            val rename = {
                if (newName.isNotBlank()) {
                    filesTree.renameFile(nodePath, newName)
                    showRenameDialogForNodePath = null
                }
            }
            AlertDialog(
                onDismissRequest = { showRenameDialogForNodePath = null },
                text = {
                    val focusRequester = rememberTremotesfInitialFocusRequester()
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions { rename() },
                        label = { Text(stringResource(R.string.file_name)) },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = rename,
                        enabled = newName.isNotBlank()
                    ) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialogForNodePath = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

private fun TremotesfMultiSelectionState<TorrentFilesTree.Item, TorrentFilesTree.NodePath>.getSelectedItemsNodeIndexes(): List<Int> {
    val indexes = ArrayList<Int>(selectedKeys.size)
    selectedKeys.mapTo(indexes) { it.indices.last() }
    indexes.sort()
    return indexes
}

private fun computeSelectedItemsPriority(
    selectionState: TremotesfMultiSelectionState<TorrentFilesTree.Item, TorrentFilesTree.NodePath>,
    filesTree: TorrentFilesTree
): TorrentFilesTree.Item.Priority {
    var priority: TorrentFilesTree.Item.Priority? = null
    for (key in selectionState.selectedKeys) {
        val item = filesTree.getCurrentNodeChildByIndexPath(key) ?: continue
        if (priority == null) {
            priority = item.priority
            if (item.priority == TorrentFilesTree.Item.Priority.Mixed) {
                break
            }
        } else if (item.priority != priority) {
            priority = TorrentFilesTree.Item.Priority.Mixed
            break
        }
    }
    return priority ?: TorrentFilesTree.Item.Priority.Normal
}
