/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.elevation.ElevationOverlayProvider
import org.equeim.tremotesf.R


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

    protected var toolbar: Toolbar? = null
        private set

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
        addNavigationBarBottomPadding()
        navController.addOnDestinationChangedListener(destinationListener)
    }

    private fun setupToolbar() {
        toolbar = requireView().findViewById<Toolbar>(R.id.toolbar).apply {
            val activity = requiredActivity
            if (!activity.appBarConfiguration.topLevelDestinations.contains(destinationId)) {
                navigationIcon = activity.upNavigationIcon
                setNavigationContentDescription(R.string.nav_app_bar_navigate_up_description)
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
        val toolbar = checkNotNull(toolbar)
        var container = toolbar.parent as View
        if (container is AppBarLayout) {
            val appBarLayout = container
            container = (container.parent as CoordinatorLayout)
            val scrollingView = container.children.find { (it.layoutParams as CoordinatorLayout.LayoutParams).behavior is AppBarLayout.ScrollingViewBehavior }
            val placeholder = View(context).apply {
                layoutParams = CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                elevation = resources.getDimension(R.dimen.action_bar_elevation)
                setBackgroundColor(ElevationOverlayProvider(requireContext()).compositeOverlayWithThemeSurfaceColorIfNeeded(elevation))
            }
            ViewCompat.setOnApplyWindowInsetsListener(placeholder) { view, insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                if (appBarLayout.marginTop != topInset) {
                    appBarLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = topInset }
                }
                if (scrollingView != null && scrollingView.marginBottom != topInset) {
                    scrollingView.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = topInset }
                }
                if (view.layoutParams.height != topInset) {
                    view.updateLayoutParams {
                        height = topInset
                    }
                }
                insets
            }
            container.addView(placeholder)
        } else if (container.id == R.id.toolbar_container && container is FrameLayout && container.childCount == 1) {
            container.setBackgroundColor(ElevationOverlayProvider(requireContext()).compositeOverlayWithThemeSurfaceColorIfNeeded(resources.getDimension(R.dimen.action_bar_elevation)))
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                if (view.paddingTop != topInset) {
                    view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
                }
                insets
            }
        }
    }

    override fun onDestroyView() {
        toolbar = null
        navController.removeOnDestinationChangedListener(destinationListener)
        super.onDestroyView()
    }

    fun showOverflowMenu(): Boolean {
        toolbar?.apply {
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

fun Fragment.addNavigationBarBottomPadding(requestApplyInsets: Boolean = false, forceViewForPadding: View? = null) {
    fun findViewsWithTagRecursively(view: View, tag: Any, block: (View) -> Unit) {
        if (view.tag == tag) {
            block(view)
        }
        if (view is ViewGroup) {
            for (child in view.children) {
                findViewsWithTagRecursively(child, tag, block)
            }
        }
    }

    val rootView = requireView()
    var foundViews = false

    if (forceViewForPadding != null) {
        handleBottomInsetWithPadding(forceViewForPadding)
        foundViews = true
    }

    val setPaddingForRootView = when (rootView) {
        is ScrollView, is NestedScrollView, is ListView -> true
        is RecyclerView -> (rootView.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL
        else -> false
    }
    if (setPaddingForRootView) {
        handleBottomInsetWithPadding(rootView)
        foundViews = true
    }

    findViewsWithTagRecursively(rootView, getText(R.string.add_navigation_bar_padding)) {
        handleBottomInsetWithPadding(it)
        foundViews = true
    }

    findViewsWithTagRecursively(rootView, getText(R.string.add_navigation_bar_margin)) {
        handleBottomInsetWithMargin(it)
        foundViews = true
    }

    if (requestApplyInsets && foundViews) {
        rootView.requestApplyInsets()
    }
}

private fun handleBottomInsetWithPadding(view: View) {
    (view as? ViewGroup)?.clipToPadding = false
    val initialPadding = view.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        if (v.paddingBottom != (initialPadding + bottomInset)) {
            v.updatePadding(bottom = initialPadding + bottomInset)
        }
        insets
    }
}

private fun handleBottomInsetWithMargin(view: View) {
    val initialMargin = view.marginBottom
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        if (v.marginBottom != (initialMargin + bottomInset)) {
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialMargin + bottomInset
            }
        }
        insets
    }
}
