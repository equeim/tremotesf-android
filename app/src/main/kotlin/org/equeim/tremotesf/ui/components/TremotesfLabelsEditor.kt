// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TremotesfLabelsEditor(
    enabledLabels: List<String>,
    removeLabel: (String) -> Unit,
    addLabel: (String) -> Unit,
    allLabels: () -> List<String>,
    modifier: Modifier = Modifier,
    textFieldFocusRequester: FocusRequester? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
        if (enabledLabels.isEmpty()) {
            TremotesfPlaceholderText(
                text = stringResource(R.string.no_labels),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
            )
        } else {
            EnabledLabelsList(labels = enabledLabels, removeLabel = removeLabel)
        }

        var textEditValue: String by rememberSaveable { mutableStateOf("") }
        val addLabelFromTextField = {
            if (textEditValue.isNotBlank() && textEditValue !in enabledLabels) {
                addLabel(textEditValue)
                textEditValue = ""
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var expanded: Boolean by rememberSaveable { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = modifier.weight(1.0f)
            ) {
                OutlinedTextField(
                    value = textEditValue,
                    onValueChange = { textEditValue = it },
                    singleLine = true,
                    keyboardActions = KeyboardActions(
                        onAny = { addLabelFromTextField() }
                    ),
                    label = { Text(stringResource(R.string.new_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .run {
                            if (textFieldFocusRequester != null) {
                                focusRequester(textFieldFocusRequester)
                            } else {
                                this
                            }
                        },
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (label in allLabels()) {
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = label) },
                            onClick = {
                                if (label !in enabledLabels) {
                                    addLabel(label)
                                }
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            val textFieldEmpty by remember { derivedStateOf { textEditValue.isEmpty() } }
            TremotesfFilledIconButtonWithTooltip(
                icon = Icons.Filled.Add,
                textId = R.string.add,
                enabled = !textFieldEmpty,
                onClick = addLabelFromTextField,
                modifier = Modifier.graphicsLayer {
                    this.translationY = 3.5f.dp.toPx()
                }
            )
        }
    }
}

@Composable
private fun EnabledLabelsList(
    labels: List<String>,
    removeLabel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        for (label in labels) {
            InputChip(
                selected = false,
                onClick = {},
                label = {
                    Box(modifier = Modifier.height(LocalMinimumInteractiveComponentSize.current)) {
                        Text(label, modifier = Modifier.align(Alignment.Start + Alignment.CenterVertically))
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Label,
                        contentDescription = label,
                        modifier = Modifier.padding(start = Dimens.SpacingSmall)
                    )
                },
                trailingIcon = {
                    TremotesfIconButtonWithTooltip(
                        icon = Icons.Filled.Close,
                        textId = R.string.remove
                    ) { removeLabel(label) }
                },
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}

@Preview
@Composable
private fun Preview() = ComponentPreview {
    TremotesfLabelsEditor(
        enabledLabels = remember { listOf("Lool", "Hmm", "NOPE") },
        removeLabel = {},
        addLabel = {},
        allLabels = { listOf("42") }
    )
}

@Preview
@Composable
private fun PreviewEmpty() = ComponentPreview {
    TremotesfLabelsEditor(
        enabledLabels = remember { emptyList() },
        removeLabel = {},
        addLabel = {},
        allLabels = { listOf("42") }
    )
}
