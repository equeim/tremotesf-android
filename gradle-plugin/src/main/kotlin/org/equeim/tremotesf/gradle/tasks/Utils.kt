package org.equeim.tremotesf.gradle.tasks

import java.util.*
import java.util.concurrent.TimeUnit

internal fun nanosToSecondsString(nanoseconds: Long): String {
    return "%.2f".format(
        Locale.ROOT,
        nanoseconds.toDouble() / TimeUnit.SECONDS.toNanos(1).toDouble()
    )
}
