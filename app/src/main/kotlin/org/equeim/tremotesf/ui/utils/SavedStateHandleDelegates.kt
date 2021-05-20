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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("unused")
fun <T : Any> ViewModel.savedState(
    savedStateHandle: SavedStateHandle,
    initialValueProducer: () -> T
): ReadWriteProperty<ViewModel, T> =
    ViewModelSavedStateProperty(savedStateHandle, initialValueProducer)

@Suppress("unused")
fun <T : Any> ViewModel.savedState(
    savedStateHandle: SavedStateHandle,
    initialValue: T
): ReadWriteProperty<ViewModel, T> = savedState(savedStateHandle) { initialValue }

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
            flow = savedStateHandle.getLiveData<T>(property.name).apply {
                if (value == null) {
                    value = initialValueProducer()
                }
            }.asStateFlow(thisRef.viewModelScope)
        }
        return flow
    }
}

private fun <T : Any> MutableLiveData<T>.asStateFlow(scope: CoroutineScope): MutableStateFlow<T> {
    val flow = MutableStateFlow(requireNotNull(value))

    val observer = Observer<T> { flow.value = it }
    observeForever(observer)

    scope.launch(Dispatchers.Main.immediate) {
        flow.onCompletion {
            removeObserver(observer)
        }.collect {
            value = it
        }
    }

    return flow
}
