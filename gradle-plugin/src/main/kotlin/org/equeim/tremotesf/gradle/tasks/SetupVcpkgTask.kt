// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.apache.commons.lang3.SystemUtils
import org.equeim.tremotesf.gradle.utils.VCPKG_LOG_PREFIX
import org.equeim.tremotesf.gradle.utils.execAndCaptureOutput
import org.equeim.tremotesf.gradle.utils.execWithLogPrefix
import org.equeim.tremotesf.gradle.utils.vcpkgRoot
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.*

@Suppress("UnstableApiUsage")
@DisableCachingByDefault
abstract class SetupVcpkgTask : DefaultTask() {
    @get:Inject
    protected abstract val projectLayout: ProjectLayout

    @get:Inject
    protected abstract val providerFactory: ProviderFactory

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Input
    abstract val vcpkgManifestDirPath: Property<String>

    @get:InputFile
    protected val vcpkgConfigurationFile: Provider<Path> by lazy {
        vcpkgManifestDirPath.map { Path(it).resolve(VCPKG_CONFIGURATION_FILENAME) }
    }

    private val json = Json { ignoreUnknownKeys = true }

    @TaskAction
    fun setupVcpkg() {
        logger.lifecycle("Setting up vcpkg")

        val vcpkgRoot = vcpkgRoot(projectLayout)
        val bootstrapScript = vcpkgRoot.resolve(BOOTSTRAP_SCRIPT)
        logger.lifecycle("Vcpkg root is {}", vcpkgRoot)

        val commit = extractVcpkgCommit()
        logger.lifecycle("Vcpkg commit is {}", commit)

        if (vcpkgRoot.isDirectory()) {
            val currentCommit = providerFactory.execAndCaptureOutput {
                commandLine("git", "-C", vcpkgRoot.pathString, "show", "--format=format:%H", "--no-patch", "HEAD")
            }
            logger.lifecycle("Current vcpkg commit is {}", currentCommit)

            if (currentCommit == commit && bootstrapScript.exists()) {
                logger.lifecycle("Nothing to do")
                didWork = false
                return
            }
            logger.lifecycle("Running git fetch")
            execOperations.execWithLogPrefix(logger, GIT_LOG_PREFIX) {
                commandLine("git", "-C", vcpkgRoot.pathString, "fetch")
            }
        } else {
            logger.lifecycle("Running git clone")
            execOperations.execWithLogPrefix(logger, GIT_LOG_PREFIX) {
                commandLine(
                    "git",
                    "clone",
                    "--no-checkout",
                    "https://github.com/microsoft/vcpkg.git",
                    vcpkgRoot
                )
            }
        }
        logger.lifecycle("Running git checkout")
        execOperations.execWithLogPrefix(logger, GIT_LOG_PREFIX) {
            commandLine("git", "-C", vcpkgRoot.pathString, "checkout", commit)
        }

        logger.lifecycle("Running bootstrap script")
        execOperations.execWithLogPrefix(logger, VCPKG_LOG_PREFIX) {
            commandLine(vcpkgRoot.resolve(bootstrapScript).pathString, "-disableMetrics")
        }

        didWork = true
    }

    private fun extractVcpkgCommit(): String {
        val configuration = json.decodeFromString<VcpkgConfiguration>(
            vcpkgConfigurationFile.get().readText()
        )
        return with(configuration.defaultRegistry) {
            check(kind == VcpkgConfiguration.Registry.KIND_BUILTIN)
            baseline
        }
    }

    @Serializable
    private data class VcpkgConfiguration(
        @SerialName("default-registry")
        val defaultRegistry: Registry
    ) {
        @Serializable
        data class Registry(
            @SerialName("kind")
            val kind: String,
            @SerialName("baseline")
            val baseline: String
        ) {
            companion object {
                const val KIND_BUILTIN = "builtin"
            }
        }
    }

    private companion object {
        const val VCPKG_CONFIGURATION_FILENAME = "vcpkg-configuration.json"
        val BOOTSTRAP_SCRIPT = if (SystemUtils.IS_OS_WINDOWS) {
            "bootstrap-vcpkg.bat"
        } else {
            "bootstrap-vcpkg.sh"
        }
        const val GIT_LOG_PREFIX = "GIT"
    }
}
