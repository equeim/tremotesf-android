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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding

import kotlin.reflect.KProperty


fun <T : ViewBinding> viewBinding(bindingFactory: (View) -> T): ViewBindingProperty<T> {
    return ViewBindingProperty(bindingFactory)
}

class ViewBindingProperty<T : ViewBinding>(private val bindingFactory: (View) -> T) {
    private var binding: T? = null

    operator fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        return binding ?: createBinding(thisRef)
    }

    private fun createBinding(fragment: Fragment): T {
        val binding = bindingFactory(fragment.requireView())
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                this@ViewBindingProperty.binding = null
            }
        })
        this.binding = binding
        return binding
    }
}