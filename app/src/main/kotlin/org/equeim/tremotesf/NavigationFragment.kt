/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf

import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout

import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.ui.navigateUp

import org.jetbrains.anko.AnkoLogger


open class NavigationFragment(@LayoutRes contentLayoutId: Int,
                              @StringRes private val titleRes: Int = 0,
                              @MenuRes private val toolbarMenuRes: Int = 0) : Fragment(contentLayoutId), AnkoLogger {
    val activity: NavigationActivity?
        get() = super.getActivity() as NavigationActivity?
    val requiredActivity: NavigationActivity
        get() = super.requireActivity() as NavigationActivity

    lateinit var navController: NavController
        private set

    @IdRes private var destinationId = 0

    private val destinationListener: NavController.OnDestinationChangedListener = object : NavController.OnDestinationChangedListener {
        override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
            if (destination.id != destinationId) {
                onNavigatedFrom()
                navController.removeOnDestinationChangedListener(this)
            }
        }
    }

    protected var toolbar: Toolbar? = null
        private set

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = requiredActivity

        // We can't do it in onCreate() because NavController may not exist yet at that point
        // (e.g. after configuration change when all fragments are recreated)
        if (!::navController.isInitialized) {
            navController = activity.navController
            findNavDestination()
        }

        toolbar = view.findViewById<Toolbar>(R.id.toolbar).apply {
            if (activity.isTopLevelDestination(destinationId)) {
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

        setPreLollipopContentShadow()

        navController.addOnDestinationChangedListener(destinationListener)
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

    override fun onDestroyView() {
        toolbar = null
        navController.removeOnDestinationChangedListener(destinationListener)
        super.onDestroyView()
    }

    private fun setPreLollipopContentShadow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            requireView().findViewById<FrameLayout>(R.id.content_frame)?.let { contentFrame ->
                val ta = contentFrame.context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowContentOverlay))
                ta.getDrawable(0)?.let { windowContentOverlay ->
                    contentFrame.foreground = LayerDrawable(arrayOf(windowContentOverlay,
                                                                    windowContentOverlay))
                }
                ta.recycle()
            }
        }
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
    protected open fun onNavigatedFrom() = Unit
}
