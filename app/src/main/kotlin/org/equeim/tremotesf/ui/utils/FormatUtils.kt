// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.TremotesfApplication
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.TransferRate
import java.text.DecimalFormat
import kotlin.time.Duration

class FileSizeFormatter(context: Context) {
    private val sizeUnits by lazy { context.resources.getStringArray(R.array.size_units) }
    private val speedUnits by lazy { context.resources.getStringArray(R.array.speed_units) }
    private val decimalFormat = DecimalFormat("0.#")

    fun formatFileSize(size: FileSize): String = formatBytes(size.bytes, sizeUnits)

    fun formatTransferRate(speed: TransferRate): String = formatBytes(speed.bytesPerSecond, speedUnits)

    private fun formatBytes(bytes: Long, units: Array<String>): String {
        val (size, unit) = calculateSize(bytes)
        val numberString = decimalFormat.format(size)
        return units[unit].format(numberString)
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
}

@Composable
fun rememberFileSizeFormatter(): FileSizeFormatter {
    val context = LocalContext.current
    return remember(context, LocalConfiguration.current.locales) { FileSizeFormatter(context) }
}

object FormatUtils {
    @Volatile
    private var fileSizeFormatter: FileSizeFormatter = FileSizeFormatter(TremotesfApplication.instance)

    init {
        TremotesfApplication.instance.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                fileSizeFormatter = FileSizeFormatter(TremotesfApplication.instance)
            }
        }, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
    }

    @Deprecated("Migrate to FileSizeFormatter")
    fun formatFileSize(@Suppress("unused") context: Context, size: FileSize): String = fileSizeFormatter.formatFileSize(size)

    @Deprecated("Migrate to FileSizeFormatter")
    fun formatTransferRate(@Suppress("unused") context: Context, speed: TransferRate): String = fileSizeFormatter.formatTransferRate(speed)

    fun formatDuration(context: Context, duration: Duration?): String {
        if (duration == null || duration.isNegative()) {
            return "\u221E"
        }

        var seconds = duration.inWholeSeconds

        val days = seconds / 86400
        seconds %= 86400
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60

        if (days > 0) {
            return context.getString(R.string.duration_days, days, hours)
        }

        if (hours > 0) {
            return context.getString(R.string.duration_hours, hours, minutes)
        }

        if (minutes > 0) {
            return context.getString(R.string.duration_minutes, minutes, seconds)
        }

        return context.getString(R.string.duration_seconds, seconds)
    }
}
