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

package org.equeim.tremotesf.ui.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition

import android.text.InputFilter
import android.text.Spanned


class IntFilter(private val range: IntRange) : InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        if (source.isEmpty()) {
            return null
        }
        val newString = dest.substring(0, dstart) + source + dest.substring(dstart)
        try {
            if (newString.toInt() in range) {
                return null
            }
        } catch (error: NumberFormatException) {
        }
        return ""
    }
}

class DoubleFilter(private val range: ClosedFloatingPointRange<Double>) : InputFilter {
    private val parser = DecimalFormat()
    private val position = ParsePosition(0)
    private var fallbackParser: DecimalFormat? = null

    init {
        if (parser.decimalFormatSymbols.decimalSeparator != '.') {
            val symbols = DecimalFormatSymbols()
            symbols.decimalSeparator = '.'
            fallbackParser = DecimalFormat(String(), symbols)
        }
    }

    private fun fallbackParse(string: String): Number? {
        position.index = 0
        return fallbackParser?.parse(string, position)
    }

    fun parse(string: String): Double? {
        position.index = 0
        val number = parser.parse(string, position) ?: fallbackParse(string)
        if (number == null || position.index != string.length) {
            return null
        }
        return number.toDouble()
    }

    override fun filter(source: CharSequence,
                        start: Int,
                        end: Int,
                        dest: Spanned,
                        dstart: Int,
                        dend: Int): CharSequence? {
        if (source.isEmpty()) {
            return null
        }
        val newString = dest.substring(0, dstart) + source + dest.substring(dstart)
        val number = parse(newString)
        if (number == null || number !in range) {
            return ""
        }
        return null
    }
}