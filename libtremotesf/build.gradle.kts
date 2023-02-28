// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import org.equeim.tremotesf.gradle.tasks.GenerateOverlayTripletsTask
import org.equeim.tremotesf.gradle.tasks.RunVcpkgInstallTask
import org.equeim.tremotesf.gradle.tasks.SetupVcpkgTask
import org.equeim.tremotesf.gradle.utils.getCMakeInfoFromPathOrNull
import org.equeim.tremotesf.gradle.utils.qtJar
import org.equeim.tremotesf.gradle.utils.vcpkgCMakeArguments
import java.lang.module.ModuleDescriptor

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.tremotesf)
}

val ndkVersionMajor = checkNotNull(android.ndkVersion).splitToSequence('.').first()

val sdkCmakeVersion = ModuleDescriptor.Version.parse(libs.versions.sdk.cmake.get())!!
logger.lifecycle("CMake version from SDK is $sdkCmakeVersion")

val pathCmakeInfo = getCMakeInfoFromPathOrNull(providers, logger)
if (pathCmakeInfo != null) {
    logger.lifecycle("CMake executable from PATH is ${pathCmakeInfo.executablePath}")
    logger.lifecycle("CMake version from PATH is ${pathCmakeInfo.version}")
}

val cmakeVersion = if (pathCmakeInfo?.version?.let { it > sdkCmakeVersion } == true) {
    logger.lifecycle("Using CMake from PATH")
    pathCmakeInfo.version
} else {
    logger.lifecycle("Using CMake from SDK")
    sdkCmakeVersion
}

val vcpkgManifestDirPath: String = layout.projectDirectory.dir("src/main/cpp/libtremotesf").asFile.path

android {
    namespace = "org.equeim.libtremotesf"

    defaultConfig.externalNativeBuild.cmake {
        arguments(
            "-DANDROID_STL=c++_shared",
            "-DANDROID_ARM_NEON=true",
            // Fix CMake forcing gold linker
            "-DCMAKE_ANDROID_NDK_VERSION=${ndkVersionMajor}",
        )
        arguments.addAll(vcpkgCMakeArguments(
            vcpkgManifestDirPath = vcpkgManifestDirPath,
            projectLayout = layout
        ))
    }

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = cmakeVersion.toString()
    }

    packagingOptions.jniLibs.keepDebugSymbols.add("**/*.so")
}

dependencies {
    implementation(libs.timber)
}

val setupVcpkgTask by tasks.registering(SetupVcpkgTask::class) {
    vcpkgManifestDirPath.set(this@Build_gradle.vcpkgManifestDirPath)
}

val generateOverlayTripletsTask by tasks.registering(GenerateOverlayTripletsTask::class) {
    dependsOn(setupVcpkgTask)
    minSdkVersion.set(libs.versions.sdk.platform.min)
    ndkVersionMajor.set(this@Build_gradle.ndkVersionMajor)
}

/**
 * Run `vcpkg install` in a separate Gradle task instead of doing this automatically when CMake is configured
 * because we need to depend on it for [qtJar] dependency (see below in [dependencies] block),
 * and making it depend in configureCMake tasks will result in circular dependency error
 */
val runVcpkgInstallTask by tasks.registering(RunVcpkgInstallTask::class) {
    dependsOn(generateOverlayTripletsTask)
    vcpkgManifestDirPath.set(this@Build_gradle.vcpkgManifestDirPath)
    androidSdkPath.set(android.sdkDirectory.path)
    androidNdkPath.set(android.sdkDirectory.toPath().resolve("ndk/${android.ndkVersion}").toString())
}

val configureCMakeRegex = Regex("configureCMake[A-Za-z]+\\[[A-Za-z\\d-]+\\]")
tasks.matching { configureCMakeRegex.matches(it.name) }.configureEach {
    logger.lifecycle("Altering $this to depend on ${runVcpkgInstallTask.name}")
    dependsOn(runVcpkgInstallTask)
}

dependencies {
    implementation(files(qtJar(layout)).builtBy(runVcpkgInstallTask))
    api(libs.androidx.annotation)
    api(libs.threetenabp)
}
