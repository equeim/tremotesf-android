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

object DecimalFormats {
    private lateinit var genericInternal: DecimalFormat
    val generic: DecimalFormat
        get() {
            if (!::genericInternal.isInitialized) {
                resetGeneric()
            }
            return genericInternal
        }

    private lateinit var ratioInternal: DecimalFormat

    val ratio: DecimalFormat
        get() {
            if (!::ratioInternal.isInitialized) {
                resetRatio()
            }
            return ratioInternal
        }

    private fun resetGeneric() {
        genericInternal = DecimalFormat("0.#")
    }

    private fun resetRatio() {
        ratioInternal = DecimalFormat("0.00")
    }

    fun reset() {
        if (::genericInternal.isInitialized) {
            resetGeneric()
        }
        if (::ratioInternal.isInitialized) {
            resetRatio()
        }
    }
}