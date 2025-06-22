// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TremotesfFloatingActionButtonWithTooltip(
    icon: ImageVector,
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BaseTremotesfButtonWithTooltip(
        tooltipText = textId,
        modifier = modifier
    ) { tooltipText ->
        FloatingActionButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = tooltipText)
        }
    }
}
