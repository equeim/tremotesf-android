package org.equeim.tremotesf.gradle.tasks

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun ExecOperations.exec(logger: Logger, forceOutput: Boolean = false, configure: ExecSpec.() -> Unit): ExecResult {
    var commandLine: List<String>? = null

    val exec = { outputStream: OutputStream? ->
        exec {
            dropNdkEnvironmentVariables(logger)
            configure()
            commandLine = this.commandLine
            if (outputStream != null) {
                standardOutput = outputStream
                errorOutput = outputStream
            }
        }
    }

    var outputStream: ByteArrayOutputStream? = null
    return try {
        if (!logger.isInfoEnabled && !forceOutput) {
            outputStream = ByteArrayOutputStream()
            outputStream.buffered().use(exec)
        } else {
            exec(null)
        }
    } catch (e: Exception) {
        logger.error("Failed to execute {}: {}", commandLine, e)
        if (outputStream != null) {
            logger.error("Output:")
            outputStream.writeTo(System.err)
        }
        throw e
    }
}

private fun ExecSpec.dropNdkEnvironmentVariables(logger: Logger) {
    val iter = environment.iterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        if (entry.key.startsWith("ANDROID_NDK")) {
            logger.info("Dropping environment variable {} = '{}'", entry.key, entry.value)
            iter.remove()
        }
    }
}

internal fun ExecOperations.make(target: String?, workingDir: File, logger: Logger, gradle: Gradle) = exec(logger) {
    executable = MAKE
    args(defaultMakeArguments(gradle))
    if (target != null) {
        args(target)
    }
    this.workingDir = workingDir
}

internal fun ExecOperations.make(workingDir: File, logger: Logger, gradle: Gradle) = make(null, workingDir, logger, gradle)

internal fun ExecOperations.zeroCcacheStatistics(logger: Logger) {
    exec(logger) { commandLine("ccache", "-z") }
}

internal fun ExecOperations.showCcacheStatistics(logger: Logger) {
    logger.lifecycle("\nCcache statistics:")
    exec(logger, true) { commandLine("ccache", "-s") }
}

private const val MAKE = "make"
internal fun makeJobsCount(gradle: Gradle) = gradle.startParameter.maxWorkerCount
internal fun defaultMakeArguments(gradle: Gradle) = listOf("-j${makeJobsCount(gradle)}")

internal fun nanosToSecondsString(nanoseconds: Long): String {
    return "%.2f".format(Locale.ROOT, nanoseconds.toDouble() / TimeUnit.SECONDS.toNanos(1).toDouble())
}
