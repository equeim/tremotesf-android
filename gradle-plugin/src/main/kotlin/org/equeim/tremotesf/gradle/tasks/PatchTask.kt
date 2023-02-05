// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import org.apache.commons.text.StringSubstitutor
import org.equeim.tremotesf.gradle.utils.ExecInputMode
import org.equeim.tremotesf.gradle.utils.executeCommand
import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.listFilesOrdered
import java.io.File

abstract class PatchTask : DefaultTask() {
    @get:Input
    abstract val sourceDir: Property<File>

    @get:InputDirectory
    abstract val patchesDir: Property<File>

    @get:Input
    abstract val substitutionMap: MapProperty<String, String>

    @TaskAction
    fun applyPatches() {
        val patches = patchesDir.get().listFilesOrdered()
        didWork = patches.map(::applyPatch).any { it }
    }

    /**
     * Returns true if patch was applied, false if it was already applied
     */
    private fun applyPatch(file: File): Boolean {
        val path = file.path
        return when {
            path.endsWith(".patch") -> applyPatchDirectly(file)
            path.endsWith(".patch.in") -> applyPatchWithSubstitution(file)
            else -> {
                logger.error("Unknown file {} in patches directory", file)
                throw RuntimeException("Unknown file $file in patches directory")
            }
        }
    }

    private fun applyPatchDirectly(patchFile: File): Boolean {
        return applyPatchImpl(PatchInput.File(patchFile), patchFile)
    }

    private fun applyPatchWithSubstitution(patchFile: File): Boolean {
        val prefix = "$$"
        val patch = StringSubstitutor(substitutionMap.get(), prefix, prefix)
            .setEnableUndefinedVariableException(true)
            .replace(patchFile.readText())
        return applyPatchImpl(PatchInput.String(patch), patchFile)
    }

    private sealed interface PatchInput {
        @JvmInline
        value class File(val value: java.io.File) : PatchInput
        @JvmInline
        value class String(val value: kotlin.String) : PatchInput
    }

    /**
     * Returns true if patch was applied, false if it was already applied
     */
    private fun applyPatchImpl(input: PatchInput, originalPatchFile: File): Boolean {
        val (patchFile, execInputMode) = when (input) {
            is PatchInput.File -> input.value.path to null
            is PatchInput.String -> "-" to ExecInputMode.InputString(input.value)
        }
        logger.lifecycle("Checking if ${originalPatchFile.name} is already applied")
        val result = executeCommand(
            listOf(PATCH, "-p1", "-R", "--dry-run", "--force", "--fuzz=0", "--input=$patchFile"),
            logger,
            inputMode = execInputMode,
            ignoreExitStatus = true
        ) {
            directory(sourceDir.get())
        }
        return if (result.success) {
            logger.lifecycle("Already applied")
            false
        } else {
            logger.lifecycle("Applying")
            executeCommand(
                listOf(PATCH, "-p1", "--fuzz=0", "--input=$patchFile"),
                logger,
                inputMode = execInputMode,
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
