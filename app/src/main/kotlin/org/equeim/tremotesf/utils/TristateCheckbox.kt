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

import kotlin.properties.Delegates

import android.content.Context
import android.util.AttributeSet

import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.widget.CompoundButtonCompat


class TristateCheckbox(context: Context,
                       attrs: AttributeSet?) : AppCompatCheckBox(context, attrs) {
    constructor(context: Context) : this(context, null)

    enum class State {
        Checked,
        Unchecked,
        Indeterminate
    }

    override fun toggle() {
        state = when (state) {
            State.Checked -> State.Unchecked
            State.Unchecked -> State.Checked
            State.Indeterminate -> State.Checked
        }
    }

    var state by Delegates.observable(State.Unchecked) { _, oldState, state ->
        if (state != oldState) {
            isChecked = (state != State.Unchecked)
            CompoundButtonCompat.getButtonDrawable(this)?.alpha = if (state == State.Indeterminate) {
                127
            } else {
                255
            }
        }
    }
}