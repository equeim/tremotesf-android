package org.equeim.tremotesf.gradle.utils

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.File

internal const val CMAKE = "cmake"

internal enum class CMakeMode {
    Build,
    Install
}

internal fun ExecOperations.cmake(cmakeBinary: String, mode: CMakeMode, workingDir: File, buildDir: File, logger: Logger, gradle: Gradle) =
    executeCommand(logger) {
        executable = cmakeBinary
        when (mode) {
            CMakeMode.Build -> args("--build", buildDir, "--parallel", gradle.startParameter.maxWorkerCount)
            CMakeMode.Install -> args("--install", buildDir)
        }
        this.workingDir = workingDir
    }

internal fun ExecOperations.printCMakeInfo(cmakeBinary: String, logger: Logger) {
    val cmakeVersion = executeCommand(logger, ExecOutputMode.Capture) { commandLine(cmakeBinary, "--version") }.outputString()
    val whichCmake = executeCommand(logger, ExecOutputMode.Capture) { commandLine("which", cmakeBinary) }.trimmedOutputString()
    logger.lifecycle(
        "Using {} from {}",
        cmakeVersion.lineSequence().first().trim(),
        whichCmake
    )
}
