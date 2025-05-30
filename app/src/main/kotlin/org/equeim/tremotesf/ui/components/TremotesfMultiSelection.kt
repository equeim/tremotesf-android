// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.SelectAll


@Composable
fun <Item : Any, Key : Any> rememberTremotesfMultiSelectionState(
    listItems: State<List<Item>>,
    keySelector: (Item) -> Key
): TremotesfMultiSelectionState<Item, Key> {
    val coroutineScope = rememberCoroutineScope()
    return rememberSaveable(saver = TremotesfMultiSelectionState.Saver(listItems, keySelector, coroutineScope)) {
        TremotesfMultiSelectionState(
            listItems = listItems,
            keySelector = keySelector,
            savedSelectedKeys = null,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
fun <Item : Any, Key : Any> rememberTremotesfMultiSelectionState(
    listItems: List<Item>,
    keySelector: (Item) -> Key
): TremotesfMultiSelectionState<Item, Key> {
    val listItemsState = rememberUpdatedState(listItems)
    return rememberTremotesfMultiSelectionState(listItemsState, keySelector)
}

@Stable
class TremotesfMultiSelectionState<Item : Any, Key : Any>(
    private val listItems: State<List<Item>>,
    private val keySelector: (Item) -> Key,
    savedSelectedKeys: ArrayList<Key>?,
    coroutineScope: CoroutineScope,
) {
    private val _selectedKeys = SnapshotStateSet<Key>().apply {
        savedSelectedKeys?.let { addAll(it) }
    }

    val selectedKeys: Set<Key> by ::_selectedKeys
    val selectedCount: Int by derivedStateOf { _selectedKeys.size }

    val hasSelection: Boolean by derivedStateOf { _selectedKeys.isNotEmpty() }

    init {
        coroutineScope.launch {
            snapshotFlow { listItems.value }
                .collect { newList ->
                    if (_selectedKeys.isNotEmpty()) {
                        if (newList.isEmpty()) {
                            deselectAll()
                        } else {
                            val keysToRemove = _selectedKeys.filter { selected ->
                                newList.none { keySelector(it) == selected }
                            }
                            if (keysToRemove.isNotEmpty()) {
                                _selectedKeys.removeAll(keysToRemove)
                            }
                        }
                    }
                }
        }
    }

    fun isSelected(key: Key): Boolean = _selectedKeys.contains(key)

    fun select(key: Key) {
        _selectedKeys.add(key)
    }

    fun deselect(key: Key) {
        _selectedKeys.remove(key)
    }

    fun setSelected(key: Key, selected: Boolean) {
        if (selected) select(key) else deselect(key)
    }

    fun selectAll() {
        _selectedKeys.addAll(listItems.value.map(keySelector))
    }

    fun deselectAll() {
        _selectedKeys.clear()
    }

    companion object {
        fun <Item : Any, Key : Any> Saver(
            listItems: State<List<Item>>,
            keySelector: Item.() -> Key,
            coroutineScope: CoroutineScope,
        ) = Saver<TremotesfMultiSelectionState<Item, Key>, ArrayList<Key>>(
            save = { ArrayList<Key>(it._selectedKeys.size).apply { addAll(it._selectedKeys) } },
            restore = {
                TremotesfMultiSelectionState(
                    listItems = listItems,
                    keySelector = keySelector,
                    savedSelectedKeys = it,
                    coroutineScope = coroutineScope
                )
            }
        )
    }
}

@Composable
fun TremotesfMultiSelectionPanel(
    state: TremotesfMultiSelectionState<*, *>,
    @PluralsRes selectedItemsString: Int,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit)
) {
    AnimatedVisibility(
        visible = state.hasSelection,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        TremotesfMultiSelectionPanelImpl(
            state = state,
            selectedItemsString = selectedItemsString,
            // Padding is necessary for shadow to not be clipped during animation
            modifier = Modifier.padding(16.dp),
            actions = actions
        )
    }
    BackHandler(enabled = state.hasSelection) { state.deselectAll() }
}

@Composable
private fun TremotesfMultiSelectionPanelImpl(
    state: TremotesfMultiSelectionState<*, *>,
    @PluralsRes selectedItemsString: Int,
    modifier: Modifier,
    actions: @Composable (RowScope.() -> Unit)
) {
    val paneTitleText = stringResource(R.string.selection_panel)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .semantics {
                paneTitle = paneTitleText
                dismiss {
                    state.deselectAll()
                    true
                }
            }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.SpacingSmall)
                .padding(top = Dimens.SpacingBig, bottom = Dimens.SpacingSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
        ) {
            val text = pluralStringResource(selectedItemsString, state.selectedCount, state.selectedCount)
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = text
                    }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TremotesfIconButtonWithTooltip(
                    icon = Icons.Filled.Close,
                    textId = R.string.close,
                ) { state.deselectAll() }
                TremotesfIconButtonWithTooltip(
                    icon = Icons.Filled.SelectAll,
                    textId = R.string.select_all,
                ) { state.selectAll() }
                VerticalDivider(
                    Modifier
                        .height(32.dp)
                        .padding(horizontal = Dimens.SpacingSmall)
                )
                actions()
            }
        }
    }
}

@Preview
@Composable
fun TremotesfMultiSelectionPanelPreview() = ComponentPreview {
    val items = remember { mutableStateOf(listOf("Hmm")) }
    val state = rememberTremotesfMultiSelectionState(
        listItems = items,
        keySelector = { it }
    )
    LaunchedEffect(state) {
        state.select("Hmm")
    }
    TremotesfMultiSelectionPanelImpl(
        state = state,
        selectedItemsString = R.plurals.servers_selected,
        modifier = Modifier,
        actions = {
            TremotesfIconButtonWithTooltip(
                icon = Icons.Filled.Favorite,
                textId = android.R.string.ok,
            ) { state.selectAll() }
        }
    )
}

@Composable
fun selectableBackground(selected: Boolean): Color = if (selected) {
    MaterialTheme.colorScheme.surfaceContainerHighest
} else {
    Color.Unspecified
}

@Composable
fun <Key : Any> Modifier.tremotesfMultiSelectionClickable(
    state: TremotesfMultiSelectionState<*, Key>,
    key: Key,
    onClick: () -> Unit
): Modifier {
    return if (!state.hasSelection) {
        return combinedClickable(
            onClick = onClick,
            onLongClick = { state.select(key) },
            onLongClickLabel = stringResource(R.string.select_action)
        )
    } else {
        toggleable(state.isSelected(key)) {
            state.setSelected(key, it)
        }
    }
}
