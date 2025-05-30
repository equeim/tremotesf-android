// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.app.Activity
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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import org.equeim.tremotesf.R
import timber.log.Timber
import java.text.DecimalFormat
import java.text.ParsePosition

val Context.application: Application get() = applicationContext.findConcreteContext()
val Context.activity: Activity get() = findConcreteContext()

private inline fun <reified T : Context> Context.findConcreteContext(): T {
    if (this is T) return this
    var context = this
    while (context is ContextWrapper) {
        if (context is T) {
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
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Failed to navigate")
    }
}

fun NavController.popDialog() {
    if (currentDestination is DialogFragmentNavigator.Destination) {
        popBackStack()
    }
}

inline fun CheckBox.setDependentViews(
    vararg views: View,
    crossinline onCheckedChanged: (Boolean) -> Unit = {},
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

fun TextView.updateCompoundDrawables(
    start: Drawable? = null,
    top: Drawable? = null,
    end: Drawable? = null,
    bottom: Drawable? = null,
) {
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

fun EditText.handleNumberRangeError(range: IntRange, onValidValue: ((Int) -> Unit)? = null) {
    doAfterTextChanged {
        val value = it?.toString()?.let { text ->
            try {
                text.toInt()
            } catch (e: NumberFormatException) {
                Timber.e(e, "Failed to parse \"$text\" as Int")
                null
            }
        }
        textInputLayout.error = if (value == null || value !in range) {
            context.getString(R.string.number_range_error, range.first, range.last)
        } else {
            onValidValue?.invoke(value)
            null
        }
    }
}

fun EditText.handleNumberRangeError(
    range: ClosedFloatingPointRange<Double>,
    onValidValue: ((Double) -> Unit)? = null
) {
    val decimalFormat = DecimalFormat()
    doAfterTextChanged {
        val value = it?.toString()?.let { text ->
            val position = ParsePosition(0)
            val parsed = decimalFormat.parse(text, position)
            if (parsed != null && position.index == text.length) {
                parsed.toDouble()
            } else {
                try {
                    text.toDouble()
                } catch (e: NumberFormatException) {
                    Timber.e(e, "Failed to parse \"$text\" as Double")
                    null
                }
            }
        }
        textInputLayout.error = if (value == null || value !in range) {
            context.getString(R.string.number_range_error_float, range.start, range.endInclusive)
        } else {
            onValidValue?.invoke(value)
            null
        }
    }
}

inline fun <reified T : Parcelable> Bundle.parcelable(key: String?): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }

val RecyclerView.ViewHolder.bindingAdapterPositionOrNull: Int?
    get() = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }

val ViewPager2.currentItemFlow: Flow<Int>
    get() = callbackFlow {
        send(currentItem)
        val callback = object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                trySend(position)
            }
        }
        registerOnPageChangeCallback(callback)
        awaitClose { unregisterOnPageChangeCallback(callback) }
    }.conflate().distinctUntilChanged()
