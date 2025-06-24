// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.GridScope
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import org.equeim.tremotesf.ui.Dimens
import kotlin.math.roundToInt

@Composable
fun TremotesfDetailsGrid(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable GridScope.() -> Unit
) {
    val columns = remember {
        object : SimpleGridCells {
            override fun Density.calculateCrossAxisCellSizes(
                availableSize: Int,
                spacing: Int
            ): List<Int> {
                val firstColumnWidth = (availableSize / 2.5f).coerceAtMost(MAX_FIRST_COLUMN_WIDTH.toPx()).roundToInt()
                val secondColumnWidth = availableSize - firstColumnWidth - spacing
                return listOf(firstColumnWidth, secondColumnWidth)
            }

            override fun fillCellSize(): Boolean = true

            private val MAX_FIRST_COLUMN_WIDTH = 250.dp
        }
    }
    VerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingBig, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall, Alignment.Top),
        contentAlignment = contentAlignment,
        content = content
    )
}