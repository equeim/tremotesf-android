package org.equeim.tremotesf.gradle.tasks

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal const val CONFIGURE_LOG_FILE = "tremotesf.configure.log"
internal const val BUILD_LOG_FILE = "tremotesf.build.log"
internal const val INSTALL_LOG_FILE = "tremotesf.install.log"

internal const val CCACHE_PROPERTY = "org.equeim.tremotesf.ccache"

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
