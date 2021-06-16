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


package org.equeim.tremotesf.common

import java.text.Collator

import kotlin.math.sign

class AlphanumericComparator : Comparator<String> {
    private val collator = Collator.getInstance()

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null) {
            return if (s2 == null) {
                0
            } else {
                -1
            }
        }
        if (s2 == null) {
            return 1
        }

        if (s1.isEmpty()) {
            return if (s2.isEmpty()) {
                0
            } else {
                -1
            }
        }
        if (s2.isEmpty()) {
            return 1
        }

        var s1Index = 0
        val s1Length = s1.length
        var s1Slice = ""

        var s2Index = 0
        val s2Length = s2.length
        var s2Slice = ""

        var result = 0

        while (s1Index < s1Length && s2Index < s2Length) {
            val s1IsDigit = isDigit(s1[s1Index])
            val s2IsDigit = isDigit(s2[s2Index])

            if (s1IsDigit) {
                if (s2IsDigit) {
                    s1Slice = slice(s1, s1Length, s1Index, true)
                    s2Slice = slice(s2, s2Length, s2Index, true)
                    val s1Number: Long
                    try {
                        s1Number = s1Slice.toLong()
                    } catch (e: NumberFormatException) {
                        result = 1
                        break
                    }
                    val s2Number: Long
                    try {
                        s2Number = s2Slice.toLong()
                    } catch (e: NumberFormatException) {
                        result = -1
                        break
                    }
                    result = (s1Number - s2Number).sign
                } else {
                    result = -1
                }
            } else if (s2IsDigit) {
                result = 1
            } else {
                s1Slice = slice(s1, s1Length, s1Index, false)
                s2Slice = slice(s2, s2Length, s2Index, false)

                result = collator.compare(s1Slice, s2Slice)
            }

            if (result != 0) {
                break
            }

            s1Index += s1Slice.length
            s2Index += s2Slice.length
        }

        if (result == 0) {
            return s1Length - s2Length
        }

        return result
    }

    // This method supports only ASCII digits
    // Character.isDigit() is too slow on Android due to native
    // method call.
    // You can replace it with Character.isDigit() if you
    // want to use this class with OpenJDK
    private fun isDigit(ch: Char) = ch in '0'..'9'

    private fun slice(s: String, length: Int, index: Int, digit: Boolean): String {
        var i = index + 1
        while (i < length) {
            if (isDigit(s[i]) != digit) {
                break
            }
            ++i
        }
        if (index == 0 && i == length) {
            return s
        }
        return s.substring(index, i)
    }
}
