// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController


open class NavigationDialogFragment : AppCompatDialogFragment(), NavControllerProvider {
    val activity: NavigationActivity?
        get() = super.getActivity() as NavigationActivity?
    val requiredActivity: NavigationActivity
        get() = super.requireActivity() as NavigationActivity

    override lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = findNavController()
    }
}
