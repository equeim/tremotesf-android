package org.equeim.tremotesf.gradle.utils

import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations

private const val CCACHE = "ccache"

internal fun ExecOperations.zeroCcacheStatistics(logger: Logger) {
    executeCommand(logger) { commandLine(CCACHE, "-z") }
}

internal fun ExecOperations.showCcacheStatistics(logger: Logger) {
    logger.lifecycle("\nCcache statistics:")
    executeCommand(logger, ExecOutputMode.Print) { commandLine(CCACHE, "-s") }
}
