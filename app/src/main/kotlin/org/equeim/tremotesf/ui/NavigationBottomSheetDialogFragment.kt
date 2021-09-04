package org.equeim.tremotesf.ui

import android.animation.ValueAnimator
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
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

    private val behavior: BottomSheetBehavior<*>
        get() = (requireDialog() as BottomSheetDialog).behavior

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            // Disable 'peeking' when opening bottom sheet
            setOnShowListener {
                requireView().post {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
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
                getDimension(R.styleable.BottomSheetBehavior_Layout_android_elevation, 0.0f)
            val background =
                MaterialShapeDrawable.createWithElevationOverlay(requireContext(), elevation)

            val shapeAppearanceResId =
                getResourceId(R.styleable.BottomSheetBehavior_Layout_shapeAppearance, 0)
            val shapeAppearanceOverlayResId = R.style.ShapeAppearanceOverlay_Tremotesf_BottomSheet
            background.shapeAppearanceModel =
                ShapeAppearanceModel.builder(
                    requireContext(),
                    shapeAppearanceResId,
                    shapeAppearanceOverlayResId
                ).build()

            bottomSheet.background = background

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

        updateCorners()
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
