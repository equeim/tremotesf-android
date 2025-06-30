// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.ui.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.equeim.tremotesf.R
import org.equeim.tremotesf.common.AlphanumericComparator
import org.equeim.tremotesf.rpc.requests.FileSize
import org.equeim.tremotesf.rpc.requests.TransferRate
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
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

@Composable
fun formatDuration(duration: Duration): String {
    val context = LocalContext.current
    return remember(context, LocalConfiguration.current) { formatDurationImpl(duration, context) }
}

private fun formatDurationImpl(duration: Duration, context: Context): String {
    if (duration.isNegative()) return ""

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

@Composable
fun formatTorrentEta(eta: Duration?): String {
    val context = LocalContext.current
    return remember(context, LocalConfiguration.current) { formatTorrentEtaImpl(eta, context) }
}

private fun formatTorrentEtaImpl(eta: Duration?, context: Context): String {
    if (eta == null || eta.isNegative()) {
        return INFINITY_SYMBOL
    }
    return formatDurationImpl(eta, context)
}

private const val INFINITY_SYMBOL = "\u221E"

@Composable
inline fun <T> rememberLocaleDependentValue(crossinline calculation: () -> T): T =
    remember(LocalConfiguration.current.locales, calculation = calculation)

@Composable
inline fun rememberNumberFormat(crossinline formatProducer: () -> NumberFormat): NumberFormat =
    rememberLocaleDependentValue(formatProducer)

@Composable
inline fun rememberDateTimeFormatter(crossinline formatterProducer: () -> DateTimeFormatter): DateTimeFormatter =
    rememberLocaleDependentValue(formatterProducer)

@Composable
fun rememberAlphanumericComparator(): AlphanumericComparator = rememberLocaleDependentValue { AlphanumericComparator() }
