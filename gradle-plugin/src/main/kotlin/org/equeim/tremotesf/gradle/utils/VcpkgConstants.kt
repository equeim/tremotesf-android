// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

internal val ANDROID_TRIPLETS = listOf("arm-neon-android", "arm64-android", "x64-android", "x86-android")
internal val HOST_TRIPLET = if (SystemUtils.IS_OS_WINDOWS) {
    "x64-windows"
} else {
    "x64-linux"
}

private const val VCPKG_ROOT = ".vcpkg"
internal fun vcpkgRoot(projectLayout: ProjectLayout): Path =
    projectLayout.projectDirectory.asFile.toPath().resolve(VCPKG_ROOT)

private const val VCPKG_INSTALLED_DIR_PREFIX = ".vcpkg_installed_"
internal fun vcpkgInstalledDirPrefix(projectLayout: ProjectLayout): Path =
    projectLayout.projectDirectory.asFile.toPath().resolve(VCPKG_INSTALLED_DIR_PREFIX)

private const val OVERLAY_TRIPLETS_DIR = "generated/vcpkg_overlay_triplets"
internal fun overlayTripletsDir(projectLayout: ProjectLayout): Provider<Path> =
    projectLayout.buildDirectory.map { it.asFile.toPath().resolve(OVERLAY_TRIPLETS_DIR) }

fun qtJar(projectLayout: ProjectLayout): Path {
    val prefix = vcpkgInstalledDirPrefix(projectLayout)
    val triplet = ANDROID_TRIPLETS.first()
    return prefix.resolveSibling(prefix.name + triplet).resolve("$triplet/jar/Qt6Android.jar")
}

fun vcpkgCMakeArguments(
    vcpkgManifestDirPath: String,
    projectLayout: ProjectLayout
): List<String> = listOf(
    "-DCMAKE_TOOLCHAIN_FILE=${vcpkgRoot(projectLayout).resolve("scripts/buildsystems/vcpkg.cmake").pathString}",
    "-DVCPKG_HOST_TRIPLET=${HOST_TRIPLET}",
    "-DVCPKG_MANIFEST_DIR=${vcpkgManifestDirPath}",
    "-DVCPKG_MANIFEST_INSTALL=OFF",
    "-DTREMOTESF_VCPKG_INSTALLED_DIR_PREFIX=${vcpkgInstalledDirPrefix(projectLayout).pathString}",
)

internal const val VCPKG_LOG_PREFIX = "VCPKG"
