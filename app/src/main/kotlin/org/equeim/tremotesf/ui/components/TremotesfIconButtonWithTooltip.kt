// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TremotesfIconButtonWithTooltip(
    icon: ImageVector,
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    BaseTremotesfButtonWithTooltip(
        tooltipText = textId,
        modifier = modifier
    ) { tooltipText ->
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = tooltipText)
        }
    }
}

@Composable
fun TremotesfFilledIconButtonWithTooltip(
    icon: ImageVector,
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    BaseTremotesfButtonWithTooltip(
        tooltipText = textId,
        modifier = modifier
    ) { tooltipText ->
        FilledIconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = tooltipText)
        }
    }
}

@Composable
fun TremotesfIconButtonWithTooltipAndMenu(
    icon: ImageVector,
    @StringRes textId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuContent: @Composable DropdownMenuScope.() -> Unit
) {
    Box {
        var expanded: Boolean by remember { mutableStateOf(false) }
        TremotesfIconButtonWithTooltip(icon = icon, textId = textId, modifier = modifier, enabled = enabled) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val scope = object : DropdownMenuScope, ColumnScope by this {
                override fun dismiss() { expanded = false }
            }
            with(scope) { menuContent() }
        }
    }
}

interface DropdownMenuScope : ColumnScope {
    fun dismiss()
}
