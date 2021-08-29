package org.equeim.tremotesf.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.l4digital.fastscroll.FastScroller
import org.equeim.tremotesf.R

class FastScrollRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    @SuppressLint("ResourceType")
    val fastScroller = FastScroller(context).apply {
        context.withStyledAttributes(
            attrs = intArrayOf(
                R.attr.colorControlActivated,
                R.attr.colorControlNormal
            )
        ) {
            setBubbleColor(getColor(0, 0))
            setHandleColor(getColor(1, 0))
        }
    }
    private val fastScrollerMarginBottom = context.resources.getDimensionPixelSize(R.dimen.fastscroll_scrollbar_margin_bottom)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        fastScroller.attachRecyclerView(this)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (fastScroller.marginBottom != (fastScrollerMarginBottom + paddingBottom)) {
            fastScroller.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = (fastScrollerMarginBottom + paddingBottom)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fastScroller.detachRecyclerView()
    }
}