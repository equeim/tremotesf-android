// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import org.equeim.tremotesf.ui.utils.safeNavigate

interface NavControllerProvider {
    val navController: NavController

    fun navigate(directions: NavDirections, navOptions: NavOptions? = null) {
        navController.safeNavigate(directions, navOptions)
    }
}

val Fragment.navController: NavController
    get() = if (this is NavControllerProvider) {
        navController
    } else {
        findNavController()
    }

fun Fragment.navigate(directions: NavDirections, navOptions: NavOptions? = null) {
    if (this is NavControllerProvider) {
        navigate(directions, navOptions)
    } else {
        findNavController().safeNavigate(directions, navOptions)
    }
}
