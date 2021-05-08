package org.equeim.tremotesf.gradle.tasks

import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.internal.io.StreamByteBuffer
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecActionFactory
import java.io.File

internal object ExecUtils {
    const val MAKE = "make"
    fun defaultMakeArguments(gradle: Gradle) = listOf("-j${gradle.startParameter.maxWorkerCount}")

    fun Task.exec(
        execActionFactory: ExecActionFactory,
        executable: String,
        args: List<String>,
        workingDir: File,
        environment: Map<String, Any> = emptyMap(),
        ignoreExitValue: Boolean = false
    ): ExecResult = execActionFactory.newExecAction().run {
        this.executable = executable
        this.args = args
        this.workingDir = workingDir
        this.environment(environment)
        isIgnoreExitValue = ignoreExitValue

        val outputStream = StreamByteBuffer()
        standardOutput = outputStream.outputStream
        errorOutput = outputStream.outputStream
        try {
            execute()
        } catch (e: Exception) {
            logger.error("Failed to execute ${this.commandLine}, output:")
            outputStream.writeTo(System.err)
            throw e
        }
    }
}
