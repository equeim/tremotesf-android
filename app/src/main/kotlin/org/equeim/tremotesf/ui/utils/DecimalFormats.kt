// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.equeim.tremotesf.TremotesfApplication
import timber.log.Timber
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.min

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

    init {
        TremotesfApplication.instance.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.i("Locale changed, resetting decimal formats")
                reset()
            }
        }, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }

    private fun resetGeneric() {
        genericInternal = DecimalFormat("0.#")
    }

    private fun resetRatio() {
        ratioInternal = DecimalFormat("0.00")
    }

    private fun reset() {
        if (::genericInternal.isInitialized) {
            resetGeneric()
        }
        if (::ratioInternal.isInitialized) {
            resetRatio()
        }
    }
}

infix fun Double.fuzzyEquals(other: Double): Boolean {
    return (abs(this - other) * 1000000000000.0 <= min(abs(this), abs(other)))
}

infix fun Double?.fuzzyEquals(other: Double?): Boolean {
    return if (this == null) {
        other == null
    } else {
        if (other == null) {
            false
        } else {
            this fuzzyEquals other
        }
    }
}
