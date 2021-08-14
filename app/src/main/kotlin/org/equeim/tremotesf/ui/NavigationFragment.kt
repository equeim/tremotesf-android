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
import org.equeim.tremotesf.ui.utils.BottomPaddingDecoration


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

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        createStatusBarPlaceholder()
        navController.addOnDestinationChangedListener(destinationListener)
    }

    private fun setupToolbar() {
        val activity = requiredActivity
        toolbar = requireView().findViewById<Toolbar>(R.id.toolbar).apply {
            if (requiredActivity.isTopLevelDestination(destinationId)) {
                navigationIcon = activity.drawerNavigationIcon
                setNavigationContentDescription(R.string.nav_app_bar_open_drawer_description)
            } else {
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
                if (view.layoutParams.height != topInset) {
                    view.updateLayoutParams {
                        height = topInset
                    }
                }
                insets
            }
            container.addView(placeholder)
        } else {
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

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        addNavigationBarBottomPadding()
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
    val findView: (View) -> View? = { rootView ->
        val asIs = when (rootView) {
            is ScrollView, is NestedScrollView, is ListView -> true
            is RecyclerView -> (rootView.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL
            else -> false
        }
        if (asIs) {
            rootView
        } else {
            rootView.findViewWithTag(getText(R.string.add_navigation_bar_padding))
        }
    }

    val rootView = requireView()

    val viewForPadding = forceViewForPadding ?: findView(rootView)
    if (viewForPadding != null) {
        handleBottomInsetWithPadding(viewForPadding)
    }

    val viewForMargin = requireView().findViewWithTag<View>(getText(R.string.add_navigation_bar_margin))
    if (viewForMargin != null) {
        handleBottomInsetWithMargin(viewForMargin)
    }

    if (requestApplyInsets && (viewForPadding != null || viewForMargin != null)) {
        rootView.requestApplyInsets()
    }
}

private fun handleBottomInsetWithPadding(view: View) {
    if (view is RecyclerView) {
        var decoration: BottomPaddingDecoration? = null
        for (i in 0 until view.itemDecorationCount) {
            val d = view.getItemDecorationAt(i)
            if (d is BottomPaddingDecoration) {
                decoration = d
                break
            }
        }
        if (decoration == null) {
            decoration = BottomPaddingDecoration(view, null)
            view.addItemDecoration(decoration)
        }
        decoration.handleBottomInset()
    } else {
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
