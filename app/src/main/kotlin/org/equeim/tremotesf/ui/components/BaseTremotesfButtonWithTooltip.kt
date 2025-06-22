// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseTremotesfButtonWithTooltip(
    @StringRes tooltipText: Int,
    modifier: Modifier = Modifier,
    button: @Composable (tooltipText: String) -> Unit
) {
    val tooltipTextString = stringResource(tooltipText)
    // Passing modifier to TooltipBox is broken
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = {
                PlainTooltip { Text(tooltipTextString) }
                val view = LocalView.current
                LaunchedEffect(null) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
            },
            state = rememberTooltipState(),
            focusable = false
        ) {
            button(tooltipTextString)
        }
    }
}