package org.equeim.tremotesf.gradle.utils

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import java.io.File

private const val CMAKE = "cmake"

internal enum class CMakeMode {
    Build,
    Install
}

internal fun executeCMake(
    mode: CMakeMode,
    printBuildLogOnError: Boolean,
    cmakeBinaryDir: File?,
    buildDir: File,
    logger: Logger,
    gradle: Gradle
): ExecResult =
    executeCMakeImpl(
        when (mode) {
            CMakeMode.Build -> listOf(
                "--build",
                buildDir.toString(),
                "--parallel",
                gradle.startParameter.maxWorkerCount.toString(),
                "--verbose"
            )
            CMakeMode.Install -> listOf("--install", buildDir.toString(), "--verbose")
        },
        cmakeBinaryDir,
        logger,
        when (mode) {
            CMakeMode.Build -> ExecOutputMode.RedirectOutputToFile(buildDir.resolve(BUILD_LOG_FILE), printBuildLogOnError)
            CMakeMode.Install -> ExecOutputMode.RedirectOutputToFile(buildDir.resolve(INSTALL_LOG_FILE), printBuildLogOnError)
        }
    )

internal fun printCMakeInfo(cmakeBinaryDir: File?, logger: Logger) {
    val info = getCMakeInfoOrNull(cmakeBinaryDir, logger) ?: return
    logger.lifecycle("Using CMake {} from {}", info.version, info.executablePath)
}

data class CMakeInfo(val executablePath: String, val version: String)

fun getCMakeInfoOrNull(
    cmakeBinaryDir: File?,
    logger: Logger
): CMakeInfo? {
    val execResult = runCatching {
        executeCMakeImpl(listOf("--version"), cmakeBinaryDir, logger, ExecOutputMode.CaptureOutput)
    }.getOrNull() ?: return null
    val executablePath = execResult.executablePath ?: run {
        logger.error("CMake executable path is unknown")
        return null
    }
    val output = execResult.outputString()
    val version = runCatching {
        output
            .lineSequence()
            .first()
            .trim()
            .split(Regex("\\s"))
            .last()
    }.getOrElse {
        logger.error("Failed to parse output of `cmake --version`", it)
        logger.error("Output:")
        System.err.println(output)
        return null
    }
    return CMakeInfo(executablePath, version)
}

private fun executeCMakeImpl(
    args: List<String>,
    cmakeBinaryDir: File?,
    logger: Logger,
    outputMode: ExecOutputMode,
): ExecResult = executeCommand(listOf(cmakeBinaryDir?.resolve(CMAKE)?.toString() ?: CMAKE) + args, logger, outputMode = outputMode) {
    cmakeBinaryDir?.let { prependPath(it) }
}
