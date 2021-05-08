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
    @AttrRes defStyleAttr: Int = R.attr.autoCompleteTextViewStyle
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
