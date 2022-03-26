package org.equeim.tremotesf.gradle.utils

import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets

internal enum class ExecOutputMode {
    Print,
    Capture,
}

@Suppress("ArrayInDataClass")
internal data class ExecResult(val success: Boolean, val output: ByteArray?) {
    fun outputString() = checkNotNull(output).toString(StandardCharsets.UTF_8)
    fun trimmedOutputString() = outputString().trim()
}

internal fun executeCommand(
    logger: Logger,
    outputMode: ExecOutputMode?,
    execute: (Action<in ExecSpec>) -> org.gradle.process.ExecResult,
    configure: ExecSpec.() -> Unit
): ExecResult {
    var commandLine: List<String>? = null

    val executeWithOutputStream = { outputStream: OutputStream? ->
        execute {
            dropNdkEnvironmentVariables(logger)
            configure()
            commandLine = this.commandLine
            if (outputStream != null) {
                standardOutput = outputStream
                errorOutput = outputStream
            }
        }
    }

    val actualOutputMode = outputMode ?: if (logger.isInfoEnabled) {
        ExecOutputMode.Print
    } else {
        ExecOutputMode.Capture
    }

    lateinit var outputStream: ByteArrayOutputStream
    val result = try {
        when (actualOutputMode) {
            ExecOutputMode.Capture -> {
                outputStream = ByteArrayOutputStream()
                outputStream.buffered().use(executeWithOutputStream)
            }
            ExecOutputMode.Print -> executeWithOutputStream(null)
        }
    } catch (e: Exception) {
        logger.error("Failed to execute {}: {}", commandLine, e)
        if (actualOutputMode == ExecOutputMode.Capture) {
            logger.error("Output:")
            outputStream.writeTo(System.err)
        }
        throw e
    }
    return ExecResult(
        result.exitValue == 0,
        if (outputMode == ExecOutputMode.Capture) outputStream.toByteArray() else null
    )
}

internal fun ExecOperations.executeCommand(
    logger: Logger,
    outputMode: ExecOutputMode? = null,
    configure: ExecSpec.() -> Unit
) = executeCommand(logger, outputMode, this::exec, configure)

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

internal fun ExecSpec.prependPath(dir: File) {
    environment.compute("PATH") { _, value ->
        if (value == null) dir.toString() else "$dir:$value"
    }
}