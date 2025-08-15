// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

@file:OptIn(ExperimentalMaterial3Api::class)

package org.equeim.tremotesf.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.equeim.tremotesf.R

@Composable
fun TremotesfTopAppBar(
    title: String,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            TremotesfIconButtonWithTooltip(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                textId = R.string.navigate_up,
                onClick = navigateUp
            )
        },
        actions = actions,
        colors = colors(),
    )
}

@Composable
fun TremotesfScrollableTopAppBar(
    title: String,
    navigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = {
            TremotesfIconButtonWithTooltip(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                textId = R.string.navigate_up,
                onClick = navigateUp
            )
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors(),
    )
}

@Composable
fun TremotesfScrollableTopAppBarWithSubtitle(
    title: String,
    subtitle: String?,
    navigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // TODO: use TopAppBar override with subtitle parameter after update material3 to 1.5
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = TopAppBarDefaults.topAppBarColors().subtitleContentColor
                    )
                }
            }
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            TremotesfIconButtonWithTooltip(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                textId = R.string.navigate_up,
                onClick = navigateUp
            )
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors(),
    )
}

@Composable
private fun colors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = TremotesfTopAppBarDefaults.containerColor(),
    scrolledContainerColor = TremotesfTopAppBarDefaults.containerColor()
)

object TremotesfTopAppBarDefaults {
    @Composable
    @ReadOnlyComposable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh
}
