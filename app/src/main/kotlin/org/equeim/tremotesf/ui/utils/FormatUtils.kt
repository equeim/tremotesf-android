package org.equeim.tremotesf.ui.utils

import android.content.Context
import org.equeim.tremotesf.R

object FormatUtils {
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
        val (size, unit) = calculateSize(bytes)
        val numberString = DecimalFormats.generic.format(size)
        return context.resources.getStringArray(R.array.size_units)[unit].format(numberString)
    }

    fun formatByteSpeed(context: Context, bytes: Long): String {
        val (size, unit) = calculateSize(bytes)
        val numberString = DecimalFormats.generic.format(size)
        return context.resources.getStringArray(R.array.speed_units)[unit].format(numberString)
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
