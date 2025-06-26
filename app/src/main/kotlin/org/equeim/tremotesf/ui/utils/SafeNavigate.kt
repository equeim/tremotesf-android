// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import timber.log.Timber

fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions? = null) {
    try {
        navigate(directions, navOptions)
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Failed to navigate")
    }
}
