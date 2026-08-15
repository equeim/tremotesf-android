// SPDX-FileCopyrightText: 2017-2026 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
        val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
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
    fun screenContentPaddingVertical(): Dp = screenContentPaddingVertical(currentWindowAdaptiveInfoV2().windowSizeClass)

    private fun screenContentPaddingVertical(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }

    @Composable
    fun screenContentPaddingHorizontal(): Dp =
        screenContentPaddingHorizontal(currentWindowAdaptiveInfoV2().windowSizeClass)

    private fun screenContentPaddingHorizontal(windowSizeClass: WindowSizeClass): Dp =
        if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            BigScreenContentPadding
        } else {
            SmallScreenContentPadding
        }

    private val SmallScreenContentPadding = 16.dp
    private val BigScreenContentPadding = 24.dp
}
