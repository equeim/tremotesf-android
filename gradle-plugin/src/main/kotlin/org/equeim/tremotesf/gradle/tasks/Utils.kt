package org.equeim.tremotesf.gradle.tasks

import java.util.*
import java.util.concurrent.TimeUnit

internal const val CONFIGURE_LOG_FILE = "tremotesf.configure.log"
internal const val BUILD_LOG_FILE = "tremotesf.build.log"
internal const val INSTALL_LOG_FILE = "tremotesf.install.log"

internal fun nanosToSecondsString(nanoseconds: Long): String {
    return "%.2f".format(
        Locale.ROOT,
        nanoseconds.toDouble() / TimeUnit.SECONDS.toNanos(1).toDouble()
    )
}
