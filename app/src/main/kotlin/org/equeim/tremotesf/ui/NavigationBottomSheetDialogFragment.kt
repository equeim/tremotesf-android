// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.Keep
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import androidx.core.content.res.getDimensionOrThrow
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.content.withStyledAttributes
import androidx.core.view.*
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted

private const val CORNERS_ANIMATION_DURATION = 200L

open class NavigationBottomSheetDialogFragment(@LayoutRes private val contentLayoutId: Int) :
    BottomSheetDialogFragment(), NavControllerProvider {
    override lateinit var navController: NavController

    private var bottomSheet: View? = null
    private var expandedToTheTop = false
    private var cornersAnimator: ValueAnimator? = null

    private val behavior: ExpandedBottomSheetBehavior
        get() = (requireDialog() as BottomSheetDialog).behavior as ExpandedBottomSheetBehavior

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(contentLayoutId, container, false)

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val view = requireView()

        val bottomSheet = view.parent as View
        this.bottomSheet = bottomSheet

        var shouldAddInitialTopPadding = true

        (requireActivity() as NavigationActivity).windowInsets.launchAndCollectWhenStarted(viewLifecycleOwner) { insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            if (shouldAddInitialTopPadding && bottomSheet.measuredHeight != 0) {
                shouldAddInitialTopPadding = false
                if (!bottomSheet.isLaidOut) {
                    val windowManager = requireContext().getSystemService<WindowManager>()
                    val windowHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        windowManager?.currentWindowMetrics?.bounds?.height()
                    } else {
                        @Suppress("deprecation")
                        windowManager?.defaultDisplay?.let { display ->
                            val size = Point()
                            display.getSize(size)
                            size.y
                        }
                    }
                    val aboutToBeExpandedToTheTop =
                        windowHeight != null && (bottomSheet.measuredHeight + systemBars.top) >= windowHeight
                    if (aboutToBeExpandedToTheTop && bottomSheet.paddingTop != systemBars.top) {
                        bottomSheet.updatePadding(top = systemBars.top)
                    }
                }
            }

            bottomSheet.apply {
                if (marginLeft != systemBars.left || marginRight != systemBars.right) {
                    updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        leftMargin = systemBars.left
                        rightMargin = systemBars.right
                    }
                }
            }
            updateCorners()
        }

        // In order to decide ourselves when we want to change corners radius
        // we need to unset shapeAppearance in bottomSheetStyle (see styles.xml)
        // In that case BottomSheetBehavior won't create MaterialShapeDrawable background
        // and won't animate its corners
        // We then need to set our own background and animator

        requireContext().withStyledAttributes(
            com.google.android.material.R.style.Widget_MaterialComponents_BottomSheet_Modal,
            com.google.android.material.R.styleable.BottomSheetBehavior_Layout
        ) {
            val elevation =
                getDimensionOrThrow(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_android_elevation)
            val background =
                MaterialShapeDrawable.createWithElevationOverlay(requireContext(), elevation)

            val shapeAppearanceResId =
                getResourceIdOrThrow(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_shapeAppearance)
            val shapeAppearanceOverlayResId = R.style.ShapeAppearanceOverlay_Tremotesf_BottomSheet
            background.shapeAppearanceModel =
                ShapeAppearanceModel.builder(
                    requireContext(),
                    shapeAppearanceResId,
                    shapeAppearanceOverlayResId
                ).build()

            bottomSheet.background = background
            bottomSheet.backgroundTintList = AppCompatResources.getColorStateList(
                requireContext(),
                getResourceIdOrThrow(com.google.android.material.R.styleable.BottomSheetBehavior_Layout_backgroundTint)
            )

            cornersAnimator = ValueAnimator().apply {
                duration = CORNERS_ANIMATION_DURATION
                addUpdateListener {
                    background.interpolation = animatedValue as Float
                }
            }
        }

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) = updateCorners()
            override fun onSlide(bottomSheet: View, slideOffset: Float) = updateCorners()
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheet = null
        cornersAnimator?.apply {
            cancel()
            cornersAnimator = null
        }
    }

    private fun updateCorners() {
        val bottomSheet = this.bottomSheet ?: return
        if (!bottomSheet.isLaidOut) return

        val expandedToTheTop =
            (behavior.state == BottomSheetBehavior.STATE_EXPANDED && bottomSheet.top == 0)
        if (expandedToTheTop == this.expandedToTheTop) return
        this.expandedToTheTop = expandedToTheTop
        cornersAnimator?.apply {
            if (isRunning) {
                reverse()
            } else {
                if (expandedToTheTop) setFloatValues(1.0f, 0.0f) else setFloatValues(0.0f, 1.0f)
                start()
            }
        }
    }
}

@Keep
class ExpandedBottomSheetBehavior(context: Context, attrs: AttributeSet?) :
    BottomSheetBehavior<View>(context, attrs) {
    init {
        state = STATE_EXPANDED
    }

    private val callbacks = mutableListOf<BottomSheetCallback>()
    private var firstLayout = true

    @Suppress("deprecation")
    override fun setBottomSheetCallback(callback: BottomSheetCallback?) {
        super.setBottomSheetCallback(callback)
        callbacks.clear()
        if (callback != null) callbacks.add(callback)
    }

    override fun addBottomSheetCallback(callback: BottomSheetCallback) {
        super.addBottomSheetCallback(callback)
        callbacks.add(callback)
    }

    override fun removeBottomSheetCallback(callback: BottomSheetCallback) {
        super.removeBottomSheetCallback(callback)
        callbacks.remove(callback)
    }

    override fun onAttachedToLayoutParams(layoutParams: CoordinatorLayout.LayoutParams) {
        super.onAttachedToLayoutParams(layoutParams)
        firstLayout = true
    }

    override fun onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams()
        firstLayout = true
    }

    override fun onLayoutChild(
        parent: CoordinatorLayout,
        child: View,
        layoutDirection: Int
    ): Boolean {
        return super.onLayoutChild(parent, child, layoutDirection).also {
            if (firstLayout) {
                firstLayout = false
                // Notify callbacks that we are actually expanded
                child.post {
                    callbacks.forEach { it.onStateChanged(child, state) }
                }
            }
        }
    }
}
