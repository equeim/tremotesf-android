/*
 * Copyright (C) 2017-2019 Alexey Rochev <equeim@gmail.com>
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


package org.equeim.tremotesf.utils

import android.app.Activity
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView

import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.view.postDelayed
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.fragment.DialogFragmentNavigator

import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout


fun View.showSnackbar(message: CharSequence, length: Int, @StringRes actionText: Int = 0, action: ((View) -> Unit)? = null): Snackbar {
    val snackbar = Snackbar.make(this, message, length)
    if (actionText == 0) {
        snackbar.setAction("", action)
    } else {
        snackbar.setAction(actionText, action)
    }
    snackbar.show()
    return snackbar
}

fun View.showSnackbar(@StringRes message: Int, length: Int, @StringRes actionText: Int = 0, action: ((View) -> Unit)? = null) =
        showSnackbar(resources.getString(message), length, actionText, action)

fun ViewGroup.setChildrenEnabled(enabled: Boolean) {
    for (i in 0 until childCount) {
        getChildAt(i).isEnabled = enabled
    }
}

inline fun TextView.doAfterTextChangedAndNotEmpty(crossinline action: (text: Editable) -> Unit) = doAfterTextChanged {
    if (!it.isNullOrEmpty()) {
        action(it)
    }
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

fun Activity.hideKeyboard() {
    currentFocus?.let { focus ->
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(focus.windowToken, 0)
    }
}

fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

fun EditText.showKeyboard() {
    context.getSystemService<InputMethodManager>()?.let { imm ->
        if (requestFocus()) {
            // If you call showSoftInput() right after requestFocus()
            // it may sometimes fail. So add a 50ms delay
            postDelayed(50) {
                // Check if we are still focused
                if (isFocused) {
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
}

inline fun <reified T : Fragment> FragmentManager.findFragment(): T? {
    return fragments.find { it is T } as T?
}

inline fun <reified T : Fragment> Fragment.findFragment(): T? {
    return childFragmentManager.findFragment()
}

fun NavController.popDialog() {
    if (currentDestination is DialogFragmentNavigator.Destination) {
        popBackStack()
    }
}