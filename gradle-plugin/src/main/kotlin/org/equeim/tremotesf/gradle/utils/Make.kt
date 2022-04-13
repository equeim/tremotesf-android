package org.equeim.tremotesf.gradle.utils

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import java.io.File

private const val MAKE = "make"

internal fun executeMake(
    target: String,
    workingDir: File,
    outputFile: File,
    logger: Logger,
    gradle: Gradle,
    configure: ProcessBuilder.() -> Unit = {}
) =
    executeCommand(
        listOf(MAKE, "-j", gradle.startParameter.maxWorkerCount.toString(), target),
        logger,
        outputMode = ExecOutputMode.RedirectOutputToFile(outputFile)
    ) {
        directory(workingDir)
        configure()
    }
