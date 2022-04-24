package org.equeim.tremotesf.gradle.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.logging.Logger
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Duration

internal sealed class ExecInputMode {
    data class InputString(val input: String) : ExecInputMode()
}

internal sealed class ExecOutputMode {
    object PrintOutput : ExecOutputMode()
    object CaptureOutput : ExecOutputMode()
    data class RedirectOutputToFile(val file: File, val printOnError: Boolean) : ExecOutputMode()
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
    inputMode: ExecInputMode? = null,
    outputMode: ExecOutputMode? = null,
    ignoreExitStatus: Boolean = false,
    configure: ProcessBuilder.() -> Unit = {}
): ExecResult {
    logger.info("Executing {}", commandLine)
    val builder = ProcessBuilder(commandLine)
    builder.dropNdkEnvironmentVariables(logger)

    val adjustedOutputMode = outputMode ?: if (logger.isInfoEnabled) {
        ExecOutputMode.PrintOutput
    } else {
        ExecOutputMode.CaptureOutput
    }
    when (adjustedOutputMode) {
        is ExecOutputMode.CaptureOutput -> builder.redirectErrorStream(true)
        is ExecOutputMode.RedirectOutputToFile -> {
            logger.info("Redirecting output to file {}", adjustedOutputMode.file)
            builder.redirectErrorStream(true)
            builder.redirectOutput(adjustedOutputMode.file)
        }
        else -> Unit
    }

    configure(builder)

    val startTime = System.nanoTime()
    var outputStream: ByteArrayOutputStream? = null
    try {
        val process = builder.start()
        val exitStatus = runBlocking {
            when (inputMode) {
                is ExecInputMode.InputString -> {
                    launch(Dispatchers.IO) {
                        process.outputStream.use {
                            it.write(inputMode.input.toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
                else -> Unit
            }
            when (adjustedOutputMode) {
                is ExecOutputMode.PrintOutput -> {
                    launch(Dispatchers.IO) { process.inputStream.use { it.transferTo(System.out) } }
                    launch(Dispatchers.IO) { process.errorStream.use { it.transferTo(System.err) } }
                }
                is ExecOutputMode.CaptureOutput -> {
                    outputStream = ByteArrayOutputStream()
                    launch(Dispatchers.IO) { process.inputStream.use { it.transferTo(outputStream) } }
                }
                else -> Unit
            }
            process.waitFor()
        }
        val success = exitStatus == 0
        if (!ignoreExitStatus && !success) {
            throw RuntimeException("Exit status $exitStatus")
        }
        val endTime = System.nanoTime()
        return ExecResult(
            success,
            if (outputMode == ExecOutputMode.CaptureOutput) {
                checkNotNull(outputStream).toByteArray()
            } else {
                null
            },
            Duration.ofNanos(endTime - startTime)
        )
    } catch (e: Exception) {
        logger.error("Failed to execute {}", commandLine, e)
        if (outputMode is ExecOutputMode.RedirectOutputToFile) {
            logger.error("See process output in {}", outputMode.file)
            if (outputMode.printOnError) {
                logger.error("Process output:")
                outputMode.file.inputStream().use { it.transferTo(System.err) }
            }
        } else {
            outputStream?.let {
                logger.error("Process output:")
                it.writeTo(System.err)
            }
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
