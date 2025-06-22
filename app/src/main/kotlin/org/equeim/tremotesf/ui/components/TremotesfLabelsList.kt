// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComponentPreview
import org.equeim.tremotesf.ui.Dimens
import org.equeim.tremotesf.ui.Label

@Composable
fun TremotesfLabelsList(
    labels: List<String>,
    showEditButton: Boolean,
    onEditButtonClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            for (label in labels) {
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = label) }
                )
            }
            if (showEditButton) {
                InputChip(
                    selected = false,
                    onClick = onEditButtonClicked,
                    label = { Text(stringResource(R.string.edit_labels)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_labels)) }
                )
            }
        }
    }
}

@Preview
@Composable
fun TremotesfLabelsListPreview() = ComponentPreview {
    TremotesfLabelsList(
        labels = remember { listOf("Cat.", "Cats", "Felines", "Kitties", "AAAAAAAAAAAAAAAAAAAAAAAAAAA") },
        showEditButton = true,
        onEditButtonClicked = {}
    )
}
