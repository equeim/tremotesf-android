package org.equeim.tremotesf.gradle.tasks

import org.apache.commons.text.StringSubstitutor
import org.equeim.tremotesf.gradle.utils.ExecInputMode
import org.equeim.tremotesf.gradle.utils.executeCommand
import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.listFilesOrdered
import java.io.File

abstract class PatchTask : DefaultTask() {
    @get:Input
    abstract val sourceDir: Property<File>

    @get:Input
    abstract val patchesDir: Property<File>

    @get:Input
    abstract val substitutionMap: MapProperty<String, String>

    @TaskAction
    fun applyPatches() {
        patchesDir.get().listFilesOrdered().forEach(::maybeApplyPatch)
    }

    private fun maybeApplyPatch(file: File) {
        val name = file.name
        when {
            name.endsWith(".patch") -> applyPatch(file)
            name.endsWith(".patch.in") -> applyPatchWithSubstitution(file)
        }
    }

    private fun applyPatch(patch: File) {
        logger.lifecycle("Applying patch {}", patch)
        applyPatchImpl(patch.path)
    }

    private fun applyPatchWithSubstitution(patchIn: File) {
        logger.lifecycle("Applying patch {}", patchIn)
        val prefix = "$$"
        val patch = StringSubstitutor(substitutionMap.get(), prefix, prefix)
            .setEnableUndefinedVariableException(true)
            .replace(patchIn.readText())
        applyPatchImpl("-", patch)
    }

    private fun applyPatchImpl(patchFile: String, processInput: String? = null) {
        val inputMode = processInput?.let { ExecInputMode.InputString(it) }
        val result = executeCommand(
            listOf(PATCH, "-p1", "-R", "--dry-run", "--force", "--fuzz=0", "--input=$patchFile"),
            logger,
            inputMode = inputMode,
            ignoreExitStatus = true
        ) {
            directory(sourceDir.get())
        }
        didWork = if (result.success) {
            logger.lifecycle("Already applied")
            false
        } else {
            executeCommand(
                listOf(PATCH, "-p1", "--fuzz=0", "--input=$patchFile"),
                logger,
                inputMode = inputMode,
                ignoreExitStatus = false
            ) {
                directory(sourceDir.get())
            }
            true
        }
    }

    private companion object {
        const val PATCH = "patch"
    }
}
