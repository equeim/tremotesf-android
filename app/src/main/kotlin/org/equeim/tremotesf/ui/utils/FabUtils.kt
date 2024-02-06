// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.equeim.tremotesf.R

fun ExtendedFloatingActionButton.extendWhenImeIsHidden(
    insets: Flow<WindowInsetsCompat>,
    lifecycleOwner: LifecycleOwner,
) {
    val marginEndWhenImeIsVisible = resources.getDimensionPixelSize(R.dimen.fab_margin)
    insets
        .map { it.isVisible(WindowInsetsCompat.Type.ime()) }
        .distinctUntilChanged()
        .launchAndCollectWhenStarted(lifecycleOwner) { imeVisible ->
            isExtended = !imeVisible
            tooltipText = if (imeVisible) text else null
            updateLayoutParams<MarginLayoutParams> {
                val newGravity = if (imeVisible) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                when (this) {
                    is FrameLayout.LayoutParams -> gravity = newGravity
                    is CoordinatorLayout.LayoutParams -> gravity = newGravity
                    else -> throw IllegalStateException("Unsupported layoutParams type ${this::class.qualifiedName}")
                }
                marginEnd = if (imeVisible) {
                    marginEndWhenImeIsVisible
                } else {
                    0
                }
            }
        }
}
