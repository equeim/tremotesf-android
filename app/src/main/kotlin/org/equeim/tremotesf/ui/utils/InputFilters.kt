// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.text.InputFilter
import android.text.Spanned
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition


class IntFilter : InputFilter {
    private val range: LongRange

    constructor(range: IntRange) {
        this.range = range.first.toLong()..range.last.toLong()
    }

    constructor(range: LongRange) {
        this.range = range
    }

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source.isEmpty()) {
            return null
        }
        val newString = dest.substring(0, dstart) + source + dest.substring(dstart)
        try {
            if (newString.toLong() in range) {
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

    fun parseOrNull(string: String): Double? {
        position.index = 0
        val number = parser.parse(string, position) ?: fallbackParse(string)
        if (number == null || position.index != string.length) {
            return null
        }
        return number.toDouble()
    }

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        if (source.isEmpty()) {
            return null
        }
        val newString = dest.substring(0, dstart) + source + dest.substring(dstart)
        val number = parseOrNull(newString)
        if (number == null || number !in range) {
            return ""
        }
        return null
    }
}