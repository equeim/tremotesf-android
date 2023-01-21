/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
 *
 * This file is part of Tremotesf.
 *
 * Tremotesf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tremotesf is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.equeim.tremotesf.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.elevation.ElevationOverlayProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.utils.launchAndCollectWhenStarted
import org.equeim.tremotesf.ui.utils.viewLifecycleObject


open class NavigationFragment(
    @LayoutRes contentLayoutId: Int,
    @StringRes private val titleRes: Int = 0,
    @MenuRes private val toolbarMenuRes: Int = 0
) : Fragment(contentLayoutId), NavControllerProvider {
    val activity: NavigationActivity?
        get() = super.getActivity() as NavigationActivity?
    val requiredActivity: NavigationActivity
        get() = super.requireActivity() as NavigationActivity

    override lateinit var navController: NavController

    @IdRes
    var destinationId = 0
        private set

    private val destinationListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.id != destinationId) {
                onNavigatedFrom(destination)
                onNavigatedFrom()
            }
        }

    protected val toolbar: Toolbar by viewLifecycleObject { it.findViewById(R.id.toolbar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
        findNavDestination()
    }

    private fun findNavDestination() {
        val className = javaClass.name
        if (!findDestinationInGraph(navController.graph, className)) {
            throw RuntimeException("Didn't find NavDestination for $className")
        }
    }

    private fun findDestinationInGraph(navGraph: NavGraph, className: String): Boolean {
        for (destination in navGraph) {
            when (destination) {
                is FragmentNavigator.Destination -> {
                    if (destination.className == className) {
                        destinationId = destination.id
                        return true
                    }
                }
                is NavGraph -> {
                    if (findDestinationInGraph(destination, className)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        setupToolbar()
        createStatusBarPlaceholder()
        applyNavigationBarBottomInset()
        navController.addOnDestinationChangedListener(destinationListener)
    }

    private fun setupToolbar() {
        toolbar.apply {
            val activity = requiredActivity
            if (!activity.appBarConfiguration.topLevelDestinations.contains(destinationId)) {
                navigationIcon = activity.upNavigationIcon
                setNavigationContentDescription(androidx.navigation.ui.R.string.nav_app_bar_navigate_up_description)
            }
            setNavigationOnClickListener {
                navController.navigateUp(activity.appBarConfiguration)
            }
            if (titleRes != 0) {
                setTitle(titleRes)
            }
            if (toolbarMenuRes != 0) {
                inflateMenu(toolbarMenuRes)
                setOnMenuItemClickListener(::onToolbarMenuItemClicked)
            }
        }
    }

    private fun createStatusBarPlaceholder() {
        var container = toolbar.parent as View
        if (container is AppBarLayout) {
            val appBarLayout = container
            container = (container.parent as CoordinatorLayout)
            val scrollingView =
                container.children.find { (it.layoutParams as CoordinatorLayout.LayoutParams).behavior is AppBarLayout.ScrollingViewBehavior }
            val placeholder = View(context).apply {
                layoutParams =
                    CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                elevation = resources.getDimension(R.dimen.action_bar_elevation)
                setBackgroundColor(
                    ElevationOverlayProvider(requireContext()).compositeOverlayWithThemeSurfaceColorIfNeeded(
                        elevation
                    )
                )
            }
            requiredActivity.windowInsets.launchAndCollectWhenStarted(viewLifecycleOwner) { insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                if (appBarLayout.marginTop != topInset) {
                    appBarLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = topInset
                    }
                }
                if (scrollingView != null && scrollingView.marginBottom != topInset) {
                    scrollingView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = topInset
                    }
                }
                if (placeholder.layoutParams.height != topInset) {
                    placeholder.updateLayoutParams {
                        height = topInset
                    }
                }
            }
            container.addView(placeholder)
        } else if (container.id == R.id.toolbar_container && container is FrameLayout && container.childCount == 1) {
            container.setBackgroundColor(
                ElevationOverlayProvider(requireContext()).compositeOverlayWithThemeSurfaceColorIfNeeded(
                    resources.getDimension(R.dimen.action_bar_elevation)
                )
            )
            requiredActivity.windowInsets.launchAndCollectWhenStarted(viewLifecycleOwner) { insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                if (container.paddingTop != topInset) {
                    container.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
                }
            }
        }
    }

    override fun onDestroyView() {
        navController.removeOnDestinationChangedListener(destinationListener)
        super.onDestroyView()
    }

    fun showOverflowMenu(): Boolean {
        toolbar.apply {
            if (!isOverflowMenuShowing) {
                return showOverflowMenu()
            }
        }
        return false
    }

    protected open fun onToolbarMenuItemClicked(menuItem: MenuItem) = false
    protected open fun onNavigatedFrom(newDestination: NavDestination) = Unit
    protected open fun onNavigatedFrom() = Unit
}

fun Fragment.applyNavigationBarBottomInset() {
    val rootView = requireView()
    val paddingViews = rootView.findPaddingViews()
    val marginViews = rootView.findMarginViews()
    applyNavigationBarBottomInset(paddingViews, marginViews)
}

fun Fragment.applyNavigationBarBottomInset(paddingViews: Map<View, Int>, marginViews: Map<View, Int>): Job {
    return (requireActivity() as NavigationActivity).windowInsets
        .map { it.bottomNavigationBarInsetIfImeIsHidden() }
        .distinctUntilChanged()
        .launchAndCollectWhenStarted(viewLifecycleOwner) { inset ->
            paddingViews.forEach { (view, initialPadding) ->
                view.applyNavigationBarBottomInsetAsPadding(inset, initialPadding)
            }
            marginViews.forEach { (view, initialMargin) ->
                view.applyNavigationBarBottomInsetAsMargin(inset, initialMargin)
            }
        }
}

private fun WindowInsetsCompat.bottomNavigationBarInsetIfImeIsHidden(): Int =
    if (!isVisible(WindowInsetsCompat.Type.ime())) {
        getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    } else {
        0
    }

private fun View.applyNavigationBarBottomInsetAsPadding(inset: Int, initialPadding: Int) {
    val padding = initialPadding + inset
    if (paddingBottom != padding) {
        updatePadding(bottom = padding)
    }
}

private fun View.applyNavigationBarBottomInsetAsMargin(inset: Int, initialMargin: Int) {
    val margin = initialMargin + inset
    if (marginBottom != margin) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = margin
        }
    }
}

private fun View.findPaddingViews(): Map<View, Int> = buildMap {
    if (this@findPaddingViews.isVerticalScrollView) {
        put(this@findPaddingViews, paddingBottom)
    }
    findViewsWithTagRecursively(this@findPaddingViews, context.getText(R.string.add_navigation_bar_padding)) {
        put(it, it.paddingBottom)
    }
    forEach { (view, _) ->
        (view as? ViewGroup)?.clipToPadding = false
    }
}

private val View.isVerticalScrollView: Boolean
    get() = when (this) {
        is ScrollView, is NestedScrollView, is ListView -> true
        is RecyclerView -> (layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL
        else -> false
    }

private fun View.findMarginViews(): Map<View, Int> = buildMap {
    findViewsWithTagRecursively(this@findMarginViews, context.getText(R.string.add_navigation_bar_margin)) {
        put(it, it.marginBottom)
    }
}

private fun findViewsWithTagRecursively(view: View, tag: Any, onFound: (View) -> Unit) {
    if (view.tag == tag) {
        onFound(view)
    }
    when (view) {
        is FragmentContainerView, is RecyclerView, is ViewPager2 -> Unit
        is ViewGroup -> {
            for (child in view.children) {
                findViewsWithTagRecursively(child, tag, onFound)
            }
        }
    }
}
