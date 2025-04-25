// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import timber.log.Timber
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T : Any> viewLifecycleObject(initialValueProducer: ((View) -> T)? = null): ReadWriteProperty<Fragment, T> =
    NonNullableViewLifecycleObjectProperty(initialValueProducer)

fun <T : Any> viewLifecycleObjectNullable(): ReadWriteProperty<Fragment, T?> =
    NullableViewLifecycleObjectProperty()

private class NonNullableViewLifecycleObjectProperty<T : Any>(
    private val initialValueProducer: ((View) -> T)?,
) : BaseViewLifecycleObjectProperty<T>() {
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        val view = checkViewIsCreated(thisRef, property)
        registerCallback(thisRef)
        return value ?: initialValueProducer?.invoke(view)?.also {
            value = it
        } ?: throw IllegalStateException("Property ${property.name} is not initialized")
    }

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T) {
        checkViewIsCreated(thisRef, property)
        registerCallback(thisRef)
        this.value = value
    }

    private fun checkViewIsCreated(thisRef: Fragment, property: KProperty<*>): View =
        checkNotNull(thisRef.view) {
            "Property ${property.name} can be accessed only when Fragment's view is created"
        }
}

private class NullableViewLifecycleObjectProperty<T : Any> : BaseViewLifecycleObjectProperty<T?>() {
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T? {
        registerCallback(thisRef)
        return value
    }

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
        registerCallback(thisRef)
        if (thisRef.view == null && value != null) {
            Timber.e(
                RuntimeException(),
                "Property ${property.name} can't be set to non-null value after Fragment's view is destroyed (fragment is $thisRef)"
            )
            return
        }
        this.value = value
    }
}

private abstract class BaseViewLifecycleObjectProperty<T> : ReadWriteProperty<Fragment, T> {
    protected var value: T? = null

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
