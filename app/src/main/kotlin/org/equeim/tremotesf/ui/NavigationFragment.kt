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

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDirections
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.MaterialShapeUtils

import org.equeim.tremotesf.R
import org.equeim.tremotesf.ui.utils.findChildRecursively
import org.equeim.tremotesf.ui.utils.safeNavigate


open class NavigationFragment(@LayoutRes contentLayoutId: Int,
                              @StringRes private val titleRes: Int = 0,
                              @MenuRes private val toolbarMenuRes: Int = 0) : Fragment(contentLayoutId) {
    val activity: NavigationActivity?
        get() = super.getActivity() as NavigationActivity?
    val requiredActivity: NavigationActivity
        get() = super.requireActivity() as NavigationActivity

    lateinit var navController: NavController
        private set

    @IdRes var destinationId = 0
        private set

    private val destinationListener = NavController.OnDestinationChangedListener { _, destination, _ ->
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

            val parent = parent as View
            if (parent is AppBarLayout) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    MaterialShapeUtils.setElevation(parent, resources.getDimension(R.dimen.action_bar_elevation))
                }
            } else {
                parent.background = MaterialShapeDrawable.createWithElevationOverlay(requireContext(), resources.getDimension(R.dimen.action_bar_elevation))
            }
        }
    }

    private fun createStatusBarPlaceholder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val toolbar = checkNotNull(toolbar)
            var container = toolbar.parent as View
            if (container is AppBarLayout) {
                container = (container.parent as CoordinatorLayout).parent as LinearLayout
                val placeholder = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
                    background = (toolbar.parent as View).background
                }
                ViewCompat.setOnApplyWindowInsetsListener(placeholder) { view, insets ->
                    view.updateLayoutParams {
                        height = insets.systemWindowInsetTop
                    }
                    insets
                }
                container.addView(placeholder, 0)
            } else {
                ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                    view.updatePadding(top = insets.systemWindowInsetTop)
                    insets
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        addNavigationBarBottomPadding()
    }

    override fun onDestroyView() {
        toolbar = null
        navController.removeOnDestinationChangedListener(destinationListener)
        super.onDestroyView()
    }

    fun navigate(directions: NavDirections, navOptions: NavOptions? = null) {
        navController.safeNavigate(directions, navOptions)
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

fun Fragment.addNavigationBarBottomPadding(requestApplyInsets: Boolean = false) {
    val rootView = requireView()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && rootView is ViewGroup) {
        // Find scroll view
        val isScrollView = { v: View ->
            v.id != View.NO_ID && when(v) {
                is ScrollView, is NestedScrollView -> true
                is RecyclerView -> {
                    (v.layoutManager as? LinearLayoutManager)?.orientation == RecyclerView.VERTICAL
                }
                else -> false
            }
        }
        val scrollView = if (isScrollView(rootView)) {
            rootView
        } else {
            rootView.findChildRecursively(isScrollView) as ViewGroup?
        }

        // Set padding for scroll view is system gestures are enabled, or reset it to zero
        if (scrollView != null) {
            scrollView.clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
                view.updatePadding(bottom = insets.systemWindowInsetBottom)
                insets
            }
        }

        // Set margin for FAB if system gestures are enabled, or reset it to its initial margin
        val fab = rootView.findChildRecursively { it is FloatingActionButton }
        if (fab != null) {
            val initialMargin = fab.marginBottom
            ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
                val systemWindow = insets.systemWindowInsets
                if (view.marginBottom != (initialMargin + systemWindow.bottom)) {
                    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = initialMargin + systemWindow.bottom
                    }
                }
                insets
            }
        }

        if (requestApplyInsets && (scrollView != null || fab != null)) {
            rootView.requestApplyInsets()
        }
    }
}