package org.equeim.tremotesf.gradle.tasks

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun ExecOperations.exec(logger: Logger, configure: ExecSpec.() -> Unit): ExecResult {
    var commandLine: List<String>? = null
    val outputStream = ByteArrayOutputStream()
    return try {
        outputStream.buffered().use { bufferedOutputStream ->
            exec {
                configure()
                commandLine = this.commandLine
                standardOutput = bufferedOutputStream
                errorOutput = bufferedOutputStream
            }.rethrowFailure()
        }
    } catch (e: Exception) {
        logger.error("Failed to execute $commandLine: $e")
        logger.error("Output:")
        outputStream.writeTo(System.err)
        throw e
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

private const val MAKE = "make"
internal fun defaultMakeArguments(gradle: Gradle) = listOf("-j${gradle.startParameter.maxWorkerCount}")

internal fun ExecSpec.dropNdkEnvironmentVariables() {
    val iter = environment.iterator()
    while (iter.hasNext()) {
        if (iter.next().key.startsWith("ANDROID_NDK")) {
            iter.remove()
        }
    }
}

internal fun nanosToSecondsString(nanoseconds: Long): String {
    return "%.2f".format(Locale.ROOT, nanoseconds.toDouble() / TimeUnit.SECONDS.toNanos(1).toDouble())
}
