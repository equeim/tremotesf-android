// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.views

import android.content.Context
import android.text.InputType
import android.util.AttributeSet

import androidx.annotation.AttrRes
import androidx.core.content.withStyledAttributes

import com.google.android.material.textfield.MaterialAutoCompleteTextView

import org.equeim.tremotesf.R

class NonFilteringAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {
    init {
        context.withStyledAttributes(
            attrs,
            R.styleable.NonFilteringAutoCompleteTextView,
            defStyleAttr,
            0
        ) {
            if (getBoolean(R.styleable.NonFilteringAutoCompleteTextView_readOnly, false)) {
                inputType = InputType.TYPE_NULL
            }
        }
    }

    override fun performFiltering(text: CharSequence?, keyCode: Int) {}
}
