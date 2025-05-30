// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

object Dimens {
    val SpacingSmall = 8.dp
    val SpacingBig = 16.dp
    val PaddingForFAB = 70.dp
    val PaddingForSelectionPanel = 150.dp

    @Composable
    fun screenContentPadding(): PaddingValues {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val horizontal = screenContentPaddingHorizontal(windowSizeClass)
        val vertical = screenContentPaddingVertical(windowSizeClass)
        return PaddingValues(
            start = horizontal,
            top = vertical,
            end = horizontal,
            bottom = vertical
        )
    }

    @Composable
    fun screenContentPaddingVertical(): Dp = screenContentPaddingVertical(currentWindowAdaptiveInfo().windowSizeClass)

    private fun screenContentPaddingVertical(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }

    @Composable
    fun screenContentPaddingHorizontal(): Dp =
        screenContentPaddingHorizontal(currentWindowAdaptiveInfo().windowSizeClass)

    private fun screenContentPaddingHorizontal(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }

    private val SmallScreenContentPadding = 16.dp
    private val BigScreenContentPadding = 24.dp
}
