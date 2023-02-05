// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val formatterHours = DateTimeFormatter.ofPattern("H 'h' m 'm' s.SS 's'")
private val formatterMinutes = DateTimeFormatter.ofPattern("m 'm' s.SS 's'")
private val formatterSeconds = DateTimeFormatter.ofPattern("s.SS 's'")

internal fun Duration.format(): String {
    val time = LocalTime.MIDNIGHT + this
    return when {
        time.hour != 0 -> formatterHours
        time.minute != 0 -> formatterMinutes
        else -> formatterSeconds
    }.format(time)
}
