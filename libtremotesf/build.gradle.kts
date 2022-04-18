import java.lang.module.ModuleDescriptor
import org.equeim.tremotesf.gradle.tasks.OpenSSLTask
import org.equeim.tremotesf.gradle.tasks.PatchTask
import org.equeim.tremotesf.gradle.tasks.QtTask
import org.equeim.tremotesf.gradle.utils.*
import org.gradle.kotlin.dsl.libs

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.tremotesf)
}

val sdkCmakeVersion: String = libs.versions.sdk.cmake.get()
logger.lifecycle("Version of CMake from SDK is $sdkCmakeVersion")
val pathCmakeVersion = getCMakeVersionOrNull()
logger.lifecycle("Version of CMake from PATH is $pathCmakeVersion")

fun isPathCmakeNewer(): Boolean {
    if (pathCmakeVersion == null) return false
    val pathVersion = runCatching {
        ModuleDescriptor.Version.parse(pathCmakeVersion)
    }.getOrNull() ?: return false
    val sdkVersion = runCatching {
        ModuleDescriptor.Version.parse(sdkCmakeVersion)
    }.getOrNull() ?: return false
    return pathVersion > sdkVersion
}
val useCmakeFromPath = isPathCmakeNewer()
val cmakeVersion = if (useCmakeFromPath) {
    logger.lifecycle("Using CMake from PATH")
    checkNotNull(pathCmakeVersion)
} else {
    logger.lifecycle("Using CMake from SDK")
    sdkCmakeVersion
}

android {
    defaultConfig.externalNativeBuild.cmake.arguments(
        "-DANDROID_STL=c++_shared",
        "-DANDROID_ARM_NEON=true",
        "-DOPENSSL_DIR=${rootDir.resolve(OPENSSL_DIR)}",
        "-DQT_DIR=${rootDir.resolve(QT_DIR)}"
    )

    buildFeatures.buildConfig = false

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = cmakeVersion
    }

    packagingOptions.jniLibs.keepDebugSymbols.add("**/*.so")
}

dependencies {
    implementation(libs.timber)
}

val openSSLPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(OpenSSLTask.sourceDir(rootDir))
    patchesDir.set(OpenSSLTask.patchesDir(rootDir))
}

val openSSL by tasks.registering(OpenSSLTask::class) {
    dependsOn(openSSLPatches)
    rootDir.set(project.rootDir)
    minSdkVersion.set(libs.versions.sdk.platform.min)
    ndkDir.set(android.ndkDirectory)
}

val qtPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(QtTask.sourceDir(rootDir))
    patchesDir.set(QtTask.patchesDir(rootDir))
    substitutionMap.put("compileSdk", libs.versions.sdk.platform.compile)
}

val qt by tasks.registering(QtTask::class) {
    dependsOn(qtPatches)
    rootDir.set(project.rootDir)
    minSdkVersion.set(libs.versions.sdk.platform.min)
    cmakeVersion.set(this@Build_gradle.cmakeVersion)
    opensslInstallDirs.set(openSSL.map { it.installDirs.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
    if (!useCmakeFromPath) {
        cmakeBinaryDir.set(android.sdkDirectory.resolve("cmake/$sdkCmakeVersion/bin"))
    }
}

dependencies {
    implementation(files(QtTask.jar(rootDir)).builtBy(qt))
}

val clean3rdparty by tasks.registering(Delete::class) {
    delete(OpenSSLTask.dirsToClean(rootDir), QtTask.dirsToClean(rootDir))
}
