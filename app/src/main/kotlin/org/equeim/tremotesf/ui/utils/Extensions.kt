// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.view.View
import android.view.ViewParent
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout


val Context.application: Application
    get() {
        var context = applicationContext
        while (context is ContextWrapper) {
            if (context is Application) {
                return context
            }
            context = context.baseContext
        }
        throw IllegalStateException()
    }

inline fun <reified T : Fragment> FragmentManager.findFragment(): T? {
    return fragments.find { it is T } as T?
}

inline fun <reified T : Fragment> Fragment.findFragment(): T? {
    return childFragmentManager.findFragment()
}

fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions? = null) {
    try {
        navigate(directions, navOptions)
    } catch (ignore: IllegalArgumentException) {
    }
}

fun NavController.popDialog() {
    if (currentDestination is DialogFragmentNavigator.Destination) {
        popBackStack()
    }
}

fun OnBackPressedDispatcher.addCustomCallback(owner: LifecycleOwner, onBackPressed: () -> Boolean) {
    val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!onBackPressed()) {
                isEnabled = false
                this@addCustomCallback.onBackPressed()
                isEnabled = true
            }
        }
    }
    addCallback(owner, callback)
}

inline fun CheckBox.setDependentViews(
    vararg views: View,
    crossinline onCheckedChanged: (Boolean) -> Unit = {}
) {
    views.forEach { it.isEnabled = isChecked }
    setOnCheckedChangeListener { _, isChecked ->
        views.forEach {
            it.isEnabled = isChecked
        }
        onCheckedChanged(isChecked)
    }
}

inline fun TextView.doAfterTextChangedAndNotEmpty(crossinline action: (text: Editable) -> Unit) =
    doAfterTextChanged {
        if (!it.isNullOrEmpty()) {
            action(it)
        }
    }

fun TextView.updateCompoundDrawables(start: Drawable? = null, top: Drawable? = null, end: Drawable? = null, bottom: Drawable? = null) {
    val drawables = compoundDrawablesRelative
    setCompoundDrawablesRelativeWithIntrinsicBounds(
        start ?: drawables[0],
        top ?: drawables[1],
        end ?: drawables[2],
        bottom ?: drawables[3]
    )
}

val EditText.textInputLayout: TextInputLayout
    get() {
        var p: ViewParent? = parent
        while (p != null) {
            if (p is TextInputLayout) {
                return p
            }
            p = p.parent
        }
        throw IllegalArgumentException("$this is not a child of TextInputLayout")
    }

inline fun <reified T : Parcelable> Bundle.parcelable(key: String?): T? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelable(key, T::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelable(key)
}

val RecyclerView.ViewHolder.bindingAdapterPositionOrNull: Int?
    get() = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
