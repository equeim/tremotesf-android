// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.ComponentPreview

@Composable
fun TremotesfSectionHeader(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier.semantics { heading() }
    )
}

@Preview
@Composable
private fun TremotesfSectionHeaderPreview() = ComponentPreview {
    TremotesfSectionHeader(R.string.limits)
}
