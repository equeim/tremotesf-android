package org.equeim.tremotesf.ui.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.core.content.res.use
import androidx.core.graphics.withSave
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import org.equeim.tremotesf.R
import kotlin.math.roundToInt

class VerticalDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val drawable =
        context.obtainStyledAttributes(intArrayOf(android.R.attr.listDivider))
            .use { checkNotNull(it.getDrawable(0)) }
    private val bounds = Rect()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        drawVertical(c, parent)
    }

    private fun drawVertical(canvas: Canvas, parent: RecyclerView) {
        canvas.withSave {
            val left: Int
            val right: Int
            if (parent.clipToPadding) {
                left = parent.paddingLeft
                right = parent.width - parent.paddingRight
                canvas.clipRect(
                    left, parent.paddingTop, right,
                    parent.height - parent.paddingBottom
                )
            } else {
                left = 0
                right = parent.width
            }
            val childCount = parent.childCount
            // Don't draw divider after last item
            for (i in 0 until (childCount - 1)) {
                val child = parent.getChildAt(i)
                parent.getDecoratedBoundsWithMargins(child, bounds)
                val bottom = bounds.bottom + child.translationY.roundToInt()
                val top = bottom - drawable.intrinsicHeight
                drawable.setBounds(left, top, right, bottom)
                drawable.draw(canvas)
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.set(0, 0, 0, drawable.intrinsicHeight)
    }
}

class BottomPaddingDecoration(private val recyclerView: RecyclerView, @DimenRes bottomPaddingRes: Int?) : RecyclerView.ItemDecoration() {
    private val bottomPadding = bottomPaddingRes?.let { recyclerView.context.resources.getDimensionPixelSize(it) } ?: 0
    private var bottomInset = 0

    private val fastScroller: View? by lazy(LazyThreadSafetyMode.NONE) { (recyclerView.parent as View).findViewById(R.id.fast_scroller) }
    private val fastScrollerMarginBottom by lazy(LazyThreadSafetyMode.NONE) { recyclerView.context.resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_bottom) }

    init {
        recyclerView.doOnAttach { updateFastScrollerMargin() }
    }

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
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (bottomInset != this.bottomInset) {
                this.bottomInset = bottomInset
                recyclerView.invalidateItemDecorations()
            }
            updateFastScrollerMargin()
            insets
        }
    }

    private fun updateFastScrollerMargin() {
        fastScroller?.apply {
            val marginBottom = fastScrollerMarginBottom + bottomPadding + bottomInset
            if (marginBottom != this.marginBottom) {
                updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = marginBottom }
            }
        }
    }
}
