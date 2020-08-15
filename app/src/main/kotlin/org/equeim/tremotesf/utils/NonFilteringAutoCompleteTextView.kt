/*
 * Copyright (C) 2017-2020 Alexey Rochev <equeim@gmail.com>
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

import android.content.Context
import android.text.InputType
import android.util.AttributeSet

import androidx.core.content.res.use
import com.google.android.material.textfield.MaterialAutoCompleteTextView

import org.equeim.tremotesf.R

class NonFilteringAutoCompleteTextView(context: Context,
                                       attrs: AttributeSet?,
                                       defStyleAttr: Int) : MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {
    constructor(context: Context,
                attrs: AttributeSet?) : this(context, attrs, R.attr.autoCompleteTextViewStyle)
    constructor(context: Context) : this(context, null)

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.NonFilteringAutoCompleteTextView, defStyleAttr, 0)
        ta.use {
            if (ta.getBoolean(R.styleable.NonFilteringAutoCompleteTextView_readOnly, false)) {
                inputType = InputType.TYPE_NULL
            }
        }
    }

    override fun performFiltering(text: CharSequence?, keyCode: Int) {}
}
