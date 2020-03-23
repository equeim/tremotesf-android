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

import android.os.Bundle

import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions

import org.equeim.tremotesf.utils.safeNavigate


open class NavigationDialogFragment : AppCompatDialogFragment() {
    val activity: NavigationActivity?
        get() = super.getActivity() as NavigationActivity?
    val requiredActivity: NavigationActivity
        get() = super.requireActivity() as NavigationActivity

    val navController: NavController
        get() = requiredActivity.navController

    fun navigate(@IdRes resId: Int, args: Bundle? = null, navOptions: NavOptions? = null) {
        navController.safeNavigate(resId, args, navOptions)
    }
}