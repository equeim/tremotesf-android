package org.equeim.tremotesf.gradle.utils

import org.gradle.api.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration

internal sealed class ExecInputOutputMode {
    data class InputString(val input: String) : ExecInputOutputMode()
    object PrintOutput : ExecInputOutputMode()
    object CaptureOutput : ExecInputOutputMode()
    data class RedirectOutputToFile(val file: File) : ExecInputOutputMode()
}

@Suppress("ArrayInDataClass")
internal data class ExecResult(
    val success: Boolean,
    val output: ByteArray?,
    val elapsedTime: Duration
) {
    fun outputString() = checkNotNull(output).toString(StandardCharsets.UTF_8)
    fun trimmedOutputString() = outputString().trim()
}

internal fun executeCommand(
    commandLine: List<String>,
    logger: Logger,
    inputOutputMode: ExecInputOutputMode? = null,
    ignoreExitStatus: Boolean = false,
    configure: ProcessBuilder.() -> Unit = {}
): ExecResult {
    logger.info("Executing $commandLine")
    val builder = ProcessBuilder(commandLine)
    builder.dropNdkEnvironmentVariables(logger)

    val actualInputOutputMode = inputOutputMode ?: if (logger.isInfoEnabled) {
        ExecInputOutputMode.PrintOutput
    } else {
        ExecInputOutputMode.CaptureOutput
    }
    when (actualInputOutputMode) {
        is ExecInputOutputMode.InputString -> Unit
        is ExecInputOutputMode.PrintOutput,
        is ExecInputOutputMode.CaptureOutput -> builder.redirectErrorStream(true)
        is ExecInputOutputMode.RedirectOutputToFile -> {
            logger.info("Redirecting output to file ${actualInputOutputMode.file}")
            builder.redirectErrorStream(true)
            builder.redirectOutput(actualInputOutputMode.file)
        }
    }

    configure(builder)

    val startTime = System.nanoTime()
    val process = builder.start()
    lateinit var outputStream: ByteArrayOutputStream
    try {
        when (actualInputOutputMode) {
            is ExecInputOutputMode.InputString ->
                process.outputStream.use {
                    it.write(actualInputOutputMode.input.toByteArray(StandardCharsets.UTF_8))
                }
            is ExecInputOutputMode.PrintOutput -> process.inputStream.transferTo(System.out)
            is ExecInputOutputMode.CaptureOutput -> {
                outputStream = ByteArrayOutputStream()
                process.inputStream.transferTo(outputStream)
            }
            else -> Unit
        }
        val exitStatus = process.waitFor()
        val success = exitStatus == 0
        if (!ignoreExitStatus && !success) {
            throw RuntimeException("Exit status $exitStatus")
        }
        val endTime = System.nanoTime()
        return ExecResult(
            success,
            if (actualInputOutputMode == ExecInputOutputMode.CaptureOutput) {
                outputStream.toByteArray()
            } else {
                null
            },
            Duration.ofNanos(endTime - startTime)
        )
    } catch (e: Exception) {
        logger.error("Failed to execute {}: {}", commandLine, e)
        if (actualInputOutputMode == ExecInputOutputMode.CaptureOutput) {
            logger.error("Output:")
            outputStream.writeTo(System.err)
        }
        throw e
    }
}

private fun ProcessBuilder.dropNdkEnvironmentVariables(logger: Logger) {
    val iter = environment().iterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        if (entry.key.startsWith("ANDROID_NDK")) {
            logger.info("Dropping environment variable {} = '{}'", entry.key, entry.value)
            iter.remove()
        }
    }
}

internal fun ProcessBuilder.prependPath(dir: File) {
    environment().compute("PATH") { _, value ->
        if (value == null) dir.toString() else "$dir:$value"
    }
}
