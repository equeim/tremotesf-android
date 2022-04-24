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
) =
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
    val cmakeVersion = getCMakeVersion(cmakeBinaryDir, logger)
    val whichCmake = executeCommand(listOf("which", CMAKE), logger, outputMode = ExecOutputMode.CaptureOutput) {
        cmakeBinaryDir?.let { prependPath(it) }
    }.trimmedOutputString()
    logger.lifecycle(
        "Using {} from {}",
        cmakeVersion.lineSequence().first().trim(),
        whichCmake
    )
}

private fun getCMakeVersion(
    cmakeBinaryDir: File?,
    logger: Logger
) = executeCMakeImpl(listOf("--version"), cmakeBinaryDir, logger, ExecOutputMode.CaptureOutput)
    .runCatching {
        outputString()
            .lineSequence()
            .first()
            .trim()
            .split(Regex("\\s"))
            .last()
    }.getOrElse {
        throw RuntimeException("Failed to parse CMake version", it)
    }.ifEmpty { throw RuntimeException("Failed to parse CMake version") }

fun Project.getCMakeVersionOrNull(cmakeBinaryDir: File? = null) =
    runCatching { getCMakeVersion(cmakeBinaryDir, logger) }.getOrNull()

private fun executeCMakeImpl(
    args: List<String>,
    cmakeBinaryDir: File?,
    logger: Logger,
    outputMode: ExecOutputMode,
) = executeCommand(listOf(cmakeBinaryDir?.resolve(CMAKE)?.toString() ?: CMAKE) + args, logger, outputMode = outputMode) {
    cmakeBinaryDir?.let { prependPath(it) }
}
