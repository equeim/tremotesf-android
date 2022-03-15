package org.equeim.tremotesf.gradle.tasks

import org.apache.commons.text.StringSubstitutor
import org.equeim.tremotesf.gradle.utils.executeCommand
import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.listFilesOrdered
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File
import javax.inject.Inject

abstract class PatchTask @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
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
        applyPatchImpl("-") {
            standardInput = patch.byteInputStream()
        }
    }

    private fun applyPatchImpl(input: String, configure: ExecSpec.() -> Unit = {}) {
        val result = execOperations.executeCommand(logger) {
            executable = PATCH
            args("-p1", "-R", "--dry-run", "--force", "--fuzz=0", "--input=$input")
            workingDir(sourceDir)
            isIgnoreExitValue = true
            configure()
        }
        if (result.success) {
            logger.lifecycle("Already applied")
        } else {
            execOperations.executeCommand(logger) {
                executable = PATCH
                args("-p1", "--fuzz=0", "--input=$input")
                workingDir(sourceDir)
                configure()
            }
        }
    }

    private companion object {
        const val PATCH = "patch"
    }
}
