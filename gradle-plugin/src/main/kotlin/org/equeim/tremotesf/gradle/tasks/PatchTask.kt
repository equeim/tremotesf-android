package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.tasks.ExecUtils.exec
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class PatchTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val sourceDir: Property<File>

    @get:Input
    abstract val patchesDir: Property<File>

    @TaskAction
    fun applyPatches() {
        patchesDir.get().listFilesOrdered { it.extension == "patch" }.forEach(::applyPatch)
    }

    private fun applyPatch(patch: File) {
        logger.lifecycle("Applying patch {}", patch)
        val result = exec(
            execOperations,
            PATCH,
            listOf("-p1", "-R", "--dry-run", "--force", "--fuzz=0", "--input=$patch"),
            sourceDir.get(),
            ignoreExitValue = true
        )
        if (result.exitValue == 0) {
            logger.lifecycle("Already applied")
        } else {
            exec(
                execOperations,
                PATCH,
                listOf("-p1", "--fuzz=0", "--input=$patch"),
                sourceDir.get()
            )
        }
    }

    private companion object {
        const val PATCH = "patch"
    }
}
