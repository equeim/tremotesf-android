package org.equeim.tremotesf.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.delay
import org.equeim.tremotesf.R
import timber.log.Timber
import kotlin.math.abs

open class NavigationBottomSheetDialogFragment(@LayoutRes private val contentLayoutId: Int) : BottomSheetDialogFragment(), NavControllerProvider {
    override lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            // Disable 'peeking' when opening bottom sheet
            setOnShowListener {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(contentLayoutId, container, false)

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Workaround to disable corners animation when expanding bottom sheet
        // If we don't specify shapeAppearance in bottomSheetStyle (see styles.xml)
        // then BottomSheetBehavior won't create MaterialShapeDrawable background
        // and won't animate its corners
        // We then need to set our own background
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

            (view.parent as View).background = background
        }
    }
}
