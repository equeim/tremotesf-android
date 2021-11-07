/*
 * Copyright (C) 2017-2021 Alexey Rochev <equeim@gmail.com>
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

package org.equeim.tremotesf.ui.utils

import android.app.Application
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.NavArgs
import org.equeim.tremotesf.ui.navController

inline fun <T : ViewModel> Fragment.viewModelFactory(crossinline viewModelProducer: (Application) -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return viewModelProducer(requireContext().application) as T
        }
    }
}

inline fun <T : ViewModel> Fragment.savedStateViewModelFactory(crossinline viewModelProducer: (Application, SavedStateHandle) -> T): ViewModelProvider.Factory {
    return object : AbstractSavedStateViewModelFactory(this, arguments) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            @Suppress("UNCHECKED_CAST")
            return viewModelProducer(requireContext().application, handle) as T
        }
    }
}

inline fun <Args : NavArgs, VM : ViewModel> Fragment.navArgsViewModelFactory(
    argsBundle: Bundle?,
    crossinline navArgsProducer: (Bundle) -> Args,
    crossinline viewModelProducer: (Args, Application, SavedStateHandle) -> VM
): ViewModelProvider.Factory {
    return object : AbstractSavedStateViewModelFactory(this, arguments) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            @Suppress("UNCHECKED_CAST")
            return viewModelProducer(
                navArgsProducer(checkNotNull(argsBundle)),
                requireContext().application,
                handle
            ) as T
        }
    }
}

inline fun <Args : NavArgs, VM : ViewModel> Fragment.navArgsViewModelFactory(
    crossinline navArgsProducer: (Bundle) -> Args,
    crossinline viewModelProducer: (Args, Application, SavedStateHandle) -> VM
) = navArgsViewModelFactory(arguments, navArgsProducer, viewModelProducer)

inline fun <Args : NavArgs, VM : ViewModel> Fragment.navArgsViewModelFactory(
    @IdRes destinationId: Int,
    crossinline navArgsProducer: (Bundle) -> Args,
    crossinline viewModelProducer: (Args, Application, SavedStateHandle) -> VM
) = navArgsViewModelFactory(
    navController.getBackStackEntry(destinationId).arguments,
    navArgsProducer,
    viewModelProducer
)
