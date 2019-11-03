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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error


open class NavigationFragment(@LayoutRes contentLayoutId: Int,
                              @StringRes private val titleRes: Int = 0,
                              @MenuRes private val toolbarMenuRes: Int = 0) : Fragment(contentLayoutId), AnkoLogger {

    @IdRes var destinationId = 0

    private val destinationListener: NavController.OnDestinationChangedListener = object : NavController.OnDestinationChangedListener {
        override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
            if (destination.id != destinationId) {
                onNavigatedFrom()
                findNavController().removeOnDestinationChangedListener(this)
            }
        }
    }

    protected var toolbar: Toolbar? = null
        private set

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar = view.findViewById<Toolbar>(R.id.toolbar).apply {
            setupWithNavController(findNavController(), requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout))
            if (titleRes != 0) {
                setTitle(titleRes)
            }
            if (toolbarMenuRes != 0) {
                inflateMenu(toolbarMenuRes)
                setOnMenuItemClickListener(::onToolbarMenuItemClicked)
            }
        }

        setPreLollipopContentShadow()

        findNavDestination()
        findNavController().addOnDestinationChangedListener(destinationListener)
    }

    private fun findNavDestination() {
        val name = javaClass.name
        for (dest in findNavController().graph) {
            if ((dest as? FragmentNavigator.Destination)?.className == name) {
                destinationId = dest.id
                return
            }
        }
        error("Didn't find NavDestination for $name")
    }

    override fun onDestroyView() {
        toolbar = null
        findNavController().removeOnDestinationChangedListener(destinationListener)
        super.onDestroyView()
    }

    private fun setPreLollipopContentShadow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            view?.findViewById<FrameLayout>(R.id.content_frame)?.let { contentFrame ->
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

    open fun onToolbarMenuItemClicked(menuItem: MenuItem) = false
    open fun onNavigatedFrom() = Unit
}
