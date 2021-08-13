package org.equeim.tremotesf.ui.utils

import android.graphics.Rect
import android.view.View
import androidx.annotation.DimenRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

class BottomPaddingDecoration(private val recyclerView: RecyclerView, @DimenRes bottomPaddingRes: Int?) : RecyclerView.ItemDecoration() {
    private val bottomPadding = bottomPaddingRes?.let { recyclerView.context.resources.getDimensionPixelSize(it) } ?: 0
    private var bottomInset = 0

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = (view.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        if (position == checkNotNull(parent.adapter).itemCount - 1) {
            outRect.set(0, 0, 0, bottomPadding + bottomInset)
        }
    }

    fun handleBottomInset() {
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (bottomInset != this.bottomInset) {
                this.bottomInset = bottomInset
                recyclerView.invalidateItemDecorations()
            }
            insets
        }
    }
}
