package org.equeim.tremotesf.gradle.utils

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File

private const val MAKE = "make"

internal fun ExecOperations.make(target: String, workingDir: File, logger: Logger, gradle: Gradle, configure: ExecSpec.() -> Unit = {}) =
    executeCommand(logger) {
        executable = MAKE
        args(defaultMakeArguments(gradle))
        args(target)
        this.workingDir = workingDir
        configure()
    }

internal fun defaultMakeArguments(gradle: Gradle) = listOf("-j", gradle.startParameter.maxWorkerCount)
