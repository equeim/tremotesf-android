package org.equeim.tremotesf.gradle.tasks

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

internal enum class ExecOutputMode {
    Print,
    Capture,
}

@Suppress("ArrayInDataClass")
internal data class ExecResult(val success: Boolean, val output: ByteArray?) {
    fun outputString() = checkNotNull(output).toString(StandardCharsets.UTF_8)
    fun trimmedOutputString() = outputString().trim()
}

internal fun ExecOperations.exec(
    logger: Logger,
    outputMode: ExecOutputMode? = null,
    configure: ExecSpec.() -> Unit
): ExecResult {
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
                outputStream.buffered().use(exec)
            }
            ExecOutputMode.Print -> exec(null)
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

internal fun ExecOperations.make(target: String, workingDir: File, logger: Logger, gradle: Gradle, configure: ExecSpec.() -> Unit = {}) =
    exec(logger) {
        executable = MAKE
        args(defaultMakeArguments(gradle))
        args(target)
        this.workingDir = workingDir
        configure()
    }

private const val MAKE = "make"
internal fun defaultMakeArguments(gradle: Gradle) = listOf("-j", makeJobsCount(gradle).toString())

internal enum class CMakeMode {
    Build,
    Install
}

internal fun ExecOperations.cmake(cmakeBinary: String, mode: CMakeMode, workingDir: File, buildDir: File, logger: Logger, gradle: Gradle) =
    exec(logger) {
        executable = cmakeBinary
        when (mode) {
            CMakeMode.Build -> args("--build", buildDir, "--parallel", makeJobsCount(gradle))
            CMakeMode.Install -> args("--install", buildDir)
        }
        this.workingDir = workingDir
    }

internal fun ExecOperations.printCMakeInfo(cmakeBinary: String, logger: Logger) {
    val cmakeVersion = exec(logger, ExecOutputMode.Capture) { commandLine(cmakeBinary, "--version") }.outputString()
    val whichCmake = exec(logger, ExecOutputMode.Capture) { commandLine("which", cmakeBinary) }.trimmedOutputString()
    logger.lifecycle(
        "Using {} from {}",
        cmakeVersion.lineSequence().first().trim(),
        whichCmake
    )
}

internal const val CMAKE = "cmake"

internal fun ExecOperations.zeroCcacheStatistics(logger: Logger) {
    exec(logger) { commandLine(CCACHE, "-z") }
}

internal fun ExecOperations.showCcacheStatistics(logger: Logger) {
    logger.lifecycle("\nCcache statistics:")
    exec(logger, ExecOutputMode.Print) { commandLine(CCACHE, "-s") }
}

private const val CCACHE = "ccache"

internal fun makeJobsCount(gradle: Gradle) = gradle.startParameter.maxWorkerCount

internal fun nanosToSecondsString(nanoseconds: Long): String {
    return "%.2f".format(
        Locale.ROOT,
        nanoseconds.toDouble() / TimeUnit.SECONDS.toNanos(1).toDouble()
    )
}
