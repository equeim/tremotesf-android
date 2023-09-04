// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.view.Gravity
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.equeim.tremotesf.R

fun ExtendedFloatingActionButton.extendWhenImeIsHidden(insets: Flow<WindowInsetsCompat>, lifecycleOwner: LifecycleOwner) {
    val marginEndWhenImeIsVisible = resources.getDimensionPixelSize(R.dimen.fab_margin)
    insets
        .map { it.isVisible(WindowInsetsCompat.Type.ime()) }
        .distinctUntilChanged()
        .launchAndCollectWhenStarted(lifecycleOwner) { imeVisible ->
            isExtended = !imeVisible
            TooltipCompat.setTooltipText(this, if (imeVisible) text else null)
            updateLayoutParams<CoordinatorLayout.LayoutParams> {
                gravity = if (imeVisible) {
                    Gravity.BOTTOM or Gravity.END
                } else {
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                marginEnd = if (imeVisible) {
                    marginEndWhenImeIsVisible
                } else {
                    0
                }
            }
    }
}
