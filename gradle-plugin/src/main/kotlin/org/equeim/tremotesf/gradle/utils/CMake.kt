package org.equeim.tremotesf.gradle.utils

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File

private const val CMAKE = "cmake"

internal enum class CMakeMode {
    Build,
    Install
}

internal fun ExecOperations.cmake(
    mode: CMakeMode,
    cmakeBinaryDir: File?,
    buildDir: File,
    logger: Logger,
    gradle: Gradle
) =
    executeCMake(
        when (mode) {
            CMakeMode.Build -> listOf(
                "--build",
                buildDir.toString(),
                "--parallel",
                gradle.startParameter.maxWorkerCount.toString()
            )
            CMakeMode.Install -> listOf("--install", buildDir.toString())
        },
        cmakeBinaryDir,
        logger,
        null
    )

internal fun ExecOperations.printCMakeInfo(cmakeBinaryDir: File?, logger: Logger) {
    val cmakeVersion = getCMakeVersion(cmakeBinaryDir, logger)
    val whichCmake = executeCommand(logger, ExecOutputMode.Capture) {
        commandLine("which", CMAKE)
    }.trimmedOutputString()
    logger.lifecycle(
        "Using {} from {}",
        cmakeVersion.lineSequence().first().trim(),
        whichCmake
    )
}

private fun getCMakeVersion(
    cmakeBinaryDir: File?,
    logger: Logger,
    execute: (Action<in ExecSpec>) -> org.gradle.process.ExecResult
) = executeCMake(listOf("--version"), cmakeBinaryDir, logger, ExecOutputMode.Capture, execute)
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

private fun ExecOperations.getCMakeVersion(
    cmakeBinaryDir: File?,
    logger: Logger
) = getCMakeVersion(cmakeBinaryDir, logger, this::exec)

fun Project.getCMakeVersionOrNull(cmakeBinaryDir: File? = null) =
    runCatching { getCMakeVersion(cmakeBinaryDir, logger, this::exec) }.getOrNull()

private fun executeCMake(
    args: List<String>,
    cmakeBinaryDir: File?,
    logger: Logger,
    outputMode: ExecOutputMode?,
    execute: (Action<in ExecSpec>) -> org.gradle.process.ExecResult
) =
    executeCommand(logger, outputMode, execute) {
        executable = CMAKE
        setArgs(args)
        cmakeBinaryDir?.let { prependPath(it) }
    }

private fun ExecOperations.executeCMake(
    args: List<String>,
    cmakeBinaryDir: File?,
    logger: Logger,
    outputMode: ExecOutputMode?
) = executeCMake(args, cmakeBinaryDir, logger, outputMode, this::exec)