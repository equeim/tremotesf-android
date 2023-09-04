// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> savedState(
    savedStateHandle: SavedStateHandle,
    initialValueProducer: () -> T
): ReadWriteProperty<ViewModel, T> =
    SavedStateProperty(savedStateHandle, initialValueProducer)

fun <T> savedState(
    savedStateHandle: SavedStateHandle,
    initialValue: T
): ReadWriteProperty<ViewModel, T> = savedState(savedStateHandle) { initialValue }

private class SavedStateProperty<T>(
    private val savedStateHandle: SavedStateHandle,
    private val initialValueProducer: () -> T,
) : ReadWriteProperty<ViewModel, T> {
    private lateinit var key: String

    private fun getKey(property: KProperty<*>): String {
        if (!::key.isInitialized) {
            key = property.savedStateKey
        }
        return key
    }

    override fun setValue(thisRef: ViewModel, property: KProperty<*>, value: T) {
        savedStateHandle[getKey(property)] = value
    }

    override fun getValue(thisRef: ViewModel, property: KProperty<*>): T {
        val key = getKey(property)
        return if (savedStateHandle.contains(key)) {
            @Suppress("UNCHECKED_CAST", "RemoveExplicitTypeArguments")
            savedStateHandle.get<T>(key) as T
        } else {
            initialValueProducer().also {
                savedStateHandle[key] = it
            }
        }
    }
}

fun <T> savedStateFlow(
    savedStateHandle: SavedStateHandle,
    initialValueProducer: () -> T
): ReadOnlyProperty<ViewModel, SavedStateFlowHolder<T>> =
    SavedStateFlowProperty(savedStateHandle, initialValueProducer)

fun <T> savedStateFlow(
    savedStateHandle: SavedStateHandle,
    initialValue: T
): ReadOnlyProperty<ViewModel, SavedStateFlowHolder<T>> =
    savedStateFlow(savedStateHandle) { initialValue }

class SavedStateFlowHolder<T>(
    private val savedStateHandle: SavedStateHandle,
    private val key: String,
    private val initialValueProducer: () -> T
) {
    fun flow(): StateFlow<T> = savedStateHandle.getStateFlow(key, initialValueProducer())
    fun set(value: T) = savedStateHandle.set(key, value)
}

private class SavedStateFlowProperty<T>(
    private val savedStateHandle: SavedStateHandle,
    private var initialValueProducer: () -> T
) : ReadOnlyProperty<ViewModel, SavedStateFlowHolder<T>> {

    private lateinit var holder: SavedStateFlowHolder<T>

    override fun getValue(thisRef: ViewModel, property: KProperty<*>): SavedStateFlowHolder<T> {
        if (!::holder.isInitialized) {
            holder =
                SavedStateFlowHolder(savedStateHandle, property.savedStateKey, initialValueProducer)
        }
        return holder
    }
}

private val KProperty<*>.savedStateKey: String
    get() = "property_$name"
