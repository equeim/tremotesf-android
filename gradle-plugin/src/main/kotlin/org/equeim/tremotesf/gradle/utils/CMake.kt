package org.equeim.tremotesf.gradle.utils

import org.equeim.tremotesf.gradle.tasks.BUILD_LOG_FILE
import org.equeim.tremotesf.gradle.tasks.INSTALL_LOG_FILE
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
            CMakeMode.Build -> ExecInputOutputMode.RedirectOutputToFile(buildDir.resolve(BUILD_LOG_FILE))
            CMakeMode.Install -> ExecInputOutputMode.RedirectOutputToFile(buildDir.resolve(INSTALL_LOG_FILE))
        }
    )

internal fun printCMakeInfo(cmakeBinaryDir: File?, logger: Logger) {
    val cmakeVersion = getCMakeVersion(cmakeBinaryDir, logger)
    val whichCmake = executeCommand(listOf("which", CMAKE), logger, ExecInputOutputMode.CaptureOutput) {
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
) = executeCMakeImpl(listOf("--version"), cmakeBinaryDir, logger, ExecInputOutputMode.CaptureOutput)
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
    inputOutputMode: ExecInputOutputMode,
) = executeCommand(listOf(CMAKE) + args, logger, inputOutputMode) {
    cmakeBinaryDir?.let { prependPath(it) }
}