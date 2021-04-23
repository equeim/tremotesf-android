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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

inline fun <reified T : ViewModel> Fragment.viewModelFactory(crossinline viewModelProducer: (Application) -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return viewModelProducer(requireContext().application) as T
        }
    }
}

inline fun <reified T : ViewModel> Fragment.viewModels(crossinline viewModelProducer: (Application) -> T): Lazy<T> {
    return viewModels { viewModelFactory(viewModelProducer) }
}

inline fun <reified T : ViewModel> Fragment.savedStateViewModelFactory(crossinline viewModelProducer: (Application, SavedStateHandle) -> T): ViewModelProvider.Factory {
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

inline fun <reified T : ViewModel> Fragment.savedStateViewModels(crossinline viewModelProducer: (Application, SavedStateHandle) -> T): Lazy<T> {
    return viewModels { savedStateViewModelFactory(viewModelProducer) }
}
