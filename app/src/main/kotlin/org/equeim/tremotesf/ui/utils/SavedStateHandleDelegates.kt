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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("unused")
fun <T : Any> ViewModel.savedState(
    savedStateHandle: SavedStateHandle,
    initialValueProducer: () -> T
): ReadWriteProperty<ViewModel, T> =
    ViewModelSavedStateProperty(savedStateHandle, initialValueProducer)

private class ViewModelSavedStateProperty<T : Any>(
    private val savedStateHandle: SavedStateHandle,
    private val initialValueProducer: () -> T,
) : ReadWriteProperty<ViewModel, T> {

    private lateinit var value: T

    override fun setValue(thisRef: ViewModel, property: KProperty<*>, value: T) {
        this.value = value
        savedStateHandle[property.name] = value
    }

    override fun getValue(thisRef: ViewModel, property: KProperty<*>): T {
        if (!::value.isInitialized) {
            value = savedStateHandle[property.name]
                ?: initialValueProducer().also { savedStateHandle[property.name] = it }
        }
        return value
    }
}

@Suppress("unused")
fun <T : Any> ViewModel.savedStateFlow(
    savedStateHandle: SavedStateHandle,
    initialValueProducer: () -> T
): ReadOnlyProperty<ViewModel, MutableStateFlow<T>> =
    ViewModelSavedStatePropertyFlow(savedStateHandle, initialValueProducer)

private class ViewModelSavedStatePropertyFlow<T : Any>(
    private val savedStateHandle: SavedStateHandle,
    private var initialValueProducer: () -> T
) : ReadOnlyProperty<ViewModel, MutableStateFlow<T>> {

    private lateinit var flow: MutableStateFlow<T>

    override fun getValue(thisRef: ViewModel, property: KProperty<*>): MutableStateFlow<T> {
        if (!::flow.isInitialized) {
            flow = MutableStateFlow(savedStateHandle[property.name]
                ?: initialValueProducer().also { savedStateHandle[property.name] = it })
            flow
                .drop(1)
                .onEach { savedStateHandle[property.name] = it }
                .launchIn(thisRef.viewModelScope)
        }
        return flow
    }
}
