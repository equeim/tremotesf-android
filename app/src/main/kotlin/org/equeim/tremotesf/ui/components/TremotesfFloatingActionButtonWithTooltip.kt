// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TremotesfFloatingActionButtonWithTooltip(
    icon: ImageVector,
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
) {
    BaseTremotesfButtonWithTooltip(
        tooltipText = textId,
        modifier = modifier
    ) { tooltipText ->
        FloatingActionButton(
            onClick = onClick,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource
        ) {
            Icon(imageVector = icon, contentDescription = tooltipText)
        }
    }
}
