// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import org.apache.commons.lang3.SystemUtils
import org.equeim.tremotesf.gradle.utils.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.environment
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject
import kotlin.io.path.name
import kotlin.io.path.pathString

@DisableCachingByDefault
abstract class RunVcpkgInstallTask : DefaultTask() {
    @get:Inject
    protected abstract val projectLayout: ProjectLayout

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Input
    abstract val vcpkgManifestDirPath: Property<String>

    @get:Input
    abstract val androidSdkPath: Property<String>

    @get:Input
    abstract val androidNdkPath: Property<String>

    @TaskAction
    fun runVcpkgInstall() {
        val vcpkgRoot = vcpkgRoot(projectLayout)
        ANDROID_TRIPLETS.forEach { triplet ->
            logger.lifecycle("Running vcpkg install for triplet {}", triplet)
            val installedDir =
                vcpkgInstalledDirPrefix(projectLayout).run { resolveSibling(name + triplet) }.pathString
            val manifestDir = vcpkgManifestDirPath.get()
            execOperations.execWithLogPrefix(logger, VCPKG_LOG_PREFIX) {
                commandLine(
                    vcpkgRoot.resolve(VCPKG_EXECUTABLE).pathString,
                    "install",
                    "--x-install-root=$installedDir",
                    "--triplet=$triplet",
                    "--host-triplet=$HOST_TRIPLET",
                    "--overlay-triplets=${overlayTripletsDir(projectLayout).get().pathString}",
                    "--x-feature=qt6",
                    "--clean-buildtrees-after-build",
                    "--clean-packages-after-build",
                    "--no-print-usage"
                )
                workingDir(manifestDir)
                environment(
                    "ANDROID_NDK_HOME" to androidNdkPath.get(),
                    "ANDROID_SDK_HOME" to androidSdkPath.get(),
                    "VCPKG_KEEP_ENV_VARS" to "ANDROID_NDK_HOME;ANDROID_SDK_HOME"
                )
            }
        }
    }

    private companion object {
        val VCPKG_EXECUTABLE = if (SystemUtils.IS_OS_WINDOWS) {
            "vcpkg.exe"
        } else {
            "vcpkg"
        }
    }
}
