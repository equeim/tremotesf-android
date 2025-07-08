// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import my.nanihadesuka.compose.InternalLazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.equeim.tremotesf.ui.Dimens

@Composable
fun TremotesfLazyColumnScrollBar(state: LazyListState, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val settings = remember(primaryColor) {
        ScrollbarSettings.Default.copy(
            thumbSelectedColor = primaryColor,
            thumbUnselectedColor = primaryColor.copy(alpha = 0.6f)
        )
    }
    InternalLazyColumnScrollbar(
        state = state,
        settings = settings,
        modifier = modifier.padding(vertical = Dimens.SpacingSmall)
    )
}
