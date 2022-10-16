/*
 * Copyright (C) 2017-2022 Alexey Rochev <equeim@gmail.com>
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

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> viewLifecycleObject(initialValueProducer: ((View) -> T & Any)? = null): ReadWriteProperty<Fragment, T> =
    NonNullableViewLifecycleObjectProperty(initialValueProducer)

fun <T> viewLifecycleObjectNullable(initialValueProducer: ((View) -> T & Any)? = null): ReadWriteProperty<Fragment, T?> =
    NullableViewLifecycleObjectProperty(initialValueProducer)

private class NonNullableViewLifecycleObjectProperty<T>(
    private val initialValueProducer: ((View) -> T & Any)?
) : BaseViewLifecycleObjectProperty<T>() {

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        val view = checkViewIsCreated(thisRef, property)
        registerCallback(thisRef)
        return value ?: initialValueProducer?.invoke(view)?.also {
            value = it
        } ?: throw IllegalStateException("Property ${property.name} is not initialized")
    }
}

private class NullableViewLifecycleObjectProperty<T>(
    private val initialValueProducer: ((View) -> T & Any)?
) : BaseViewLifecycleObjectProperty<T?>() {

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T? {
        val view = checkViewIsCreated(thisRef, property)
        registerCallback(thisRef)
        initialValueProducer?.let { producer ->
            if (value == null) {
                value = producer(view)
            }
        }
        return value
    }
}

private abstract class BaseViewLifecycleObjectProperty<T> : ReadWriteProperty<Fragment, T> {
    protected var value: T? = null

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        checkViewIsCreated(thisRef, property)
        registerCallback(thisRef)
        this.value = value
    }

    protected fun checkViewIsCreated(thisRef: Fragment, property: KProperty<*>): View = checkNotNull(thisRef.view) {
        "Property ${property.name} can be accessed only when Fragment's view is created"
    }

    private var lifecycleCallback: FragmentLifecycleCallbacks? = null

    protected fun registerCallback(thisRef: Fragment) {
        if (lifecycleCallback != null) return
        val fragmentManager = thisRef.parentFragmentManager
        val callback = object : FragmentLifecycleCallbacks() {
            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                if (f == thisRef) {
                    value = null
                }
            }

            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                if (f == thisRef) {
                    fragmentManager.unregisterFragmentLifecycleCallbacks(this)
                }
            }
        }
        fragmentManager.registerFragmentLifecycleCallbacks(callback, false)
        lifecycleCallback = callback
    }
}
