// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.ArrayRes
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

object FormatUtils {
    private val sizeUnits = AtomicReference<Array<String>>()
    private val speedUnits = AtomicReference<Array<String>>()

    init {
        TremotesfApplication.instance.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.i("Locale changed, resetting byte units")
                sizeUnits.set(null)
                speedUnits.set(null)
            }
        }, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }

    private fun calculateSize(bytes: Long): Pair<Double, Int> {
        var unit = 0
        var size = bytes.toDouble()
        while (size >= 1024 && unit < 8) {
            size /= 1024
            unit++
        }
        return Pair(size, unit)
    }

    fun formatByteSize(context: Context, bytes: Long): String {
        return formatBytes(bytes, sizeUnits, R.array.size_units, context)
    }

    fun formatByteSpeed(context: Context, bytes: Long): String {
        return formatBytes(bytes, speedUnits, R.array.speed_units, context)
    }

    private fun formatBytes(bytes: Long, reference: AtomicReference<Array<String>>, @ArrayRes resId: Int, context: Context): String {
        val (size, unit) = calculateSize(bytes)
        val numberString = DecimalFormats.generic.format(size)
        val units = reference.get() ?: updateByteUnits(reference, resId, context)
        return units?.get(unit)?.format(numberString) ?: ""
    }

    private fun updateByteUnits(reference: AtomicReference<Array<String>>, @ArrayRes resId: Int, context: Context): Array<String>? {
        val units = context.resources.getStringArray(resId)
        return if (reference.compareAndSet(null, units)) {
            units
        } else {
            reference.get()
        }
    }

    fun formatDuration(context: Context, seconds: Int): String {
        if (seconds < 0) {
            return "\u221E"
        }

        var dseconds = seconds

        val days = dseconds / 86400
        dseconds %= 86400
        val hours = dseconds / 3600
        dseconds %= 3600
        val minutes = dseconds / 60
        dseconds %= 60

        if (days > 0) {
            return context.getString(R.string.duration_days, days, hours)
        }

        if (hours > 0) {
            return context.getString(R.string.duration_hours, hours, minutes)
        }

        if (minutes > 0) {
            return context.getString(R.string.duration_minutes, minutes, dseconds)
        }

        return context.getString(R.string.duration_seconds, dseconds)
    }
}
