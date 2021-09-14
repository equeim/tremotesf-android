package org.equeim.tremotesf.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.getDimensionOrThrow
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.equeim.tremotesf.R

private const val CORNERS_ANIMATION_DURATION = 200L

open class NavigationBottomSheetDialogFragment(@LayoutRes private val contentLayoutId: Int) :
    BottomSheetDialogFragment(), NavControllerProvider {
    override lateinit var navController: NavController

    private var lastSystemBarInsets: Insets? = null
    private var bottomSheet: View? = null
    private var roundedCorners = true
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

        ViewCompat.setOnApplyWindowInsetsListener(requireDialog().findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (lastSystemBarInsets == null && bottomSheet?.paddingTop != systemBars.top) {
                // This padding is set by BottomSheetDialog but tool late, so sometimes
                // view jumping can be observed when opening dialog
                // Set ourselves for the first time
                bottomSheet?.updatePadding(top = systemBars.top)
            }
            lastSystemBarInsets = systemBars
            view.apply {
                if (marginLeft != systemBars.left || marginRight != systemBars.right) {
                    updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        leftMargin = systemBars.left
                        rightMargin = systemBars.right
                    }
                }
                updateCorners()
            }
            insets
        }

        val bottomSheet = requireView().parent as View
        this.bottomSheet = bottomSheet

        // In order to decide ourselves when we want to change corners radius
        // we need to unset shapeAppearance in bottomSheetStyle (see styles.xml)
        // In that case BottomSheetBehavior won't create MaterialShapeDrawable background
        // and won't animate its corners
        // We then need to set our own background and animator
        requireContext().withStyledAttributes(
            R.style.Widget_MaterialComponents_BottomSheet_Modal,
            R.styleable.BottomSheetBehavior_Layout
        ) {
            val elevation =
                getDimensionOrThrow(R.styleable.BottomSheetBehavior_Layout_android_elevation)
            val background =
                MaterialShapeDrawable.createWithElevationOverlay(requireContext(), elevation)

            val shapeAppearanceResId =
                getResourceIdOrThrow(R.styleable.BottomSheetBehavior_Layout_shapeAppearance)
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
                getResourceIdOrThrow(R.styleable.BottomSheetBehavior_Layout_backgroundTint)
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
        lastSystemBarInsets = null
        bottomSheet = null
        cornersAnimator?.apply {
            cancel()
            cornersAnimator = null
        }
    }

    private fun updateCorners() {
        val roundedCorners = !(behavior.state == BottomSheetBehavior.STATE_EXPANDED &&
                lastSystemBarInsets?.top != 0 &&
                bottomSheet?.top == 0)
        if (roundedCorners == this.roundedCorners) return
        this.roundedCorners = roundedCorners
        cornersAnimator?.apply {
            if (isRunning) {
                reverse()
            } else {
                if (roundedCorners) setFloatValues(0.0f, 1.0f) else setFloatValues(1.0f, 0.0f)
                start()
            }
        }
    }
}

@Keep
class ExpandedBottomSheetBehavior(context: Context, attrs: AttributeSet?) : BottomSheetBehavior<View>(context, attrs) {
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
