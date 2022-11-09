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

val sdkCmakeVersion: ModuleDescriptor.Version = ModuleDescriptor.Version.parse(libs.versions.sdk.cmake.get())
logger.lifecycle("Version of CMake from SDK is $sdkCmakeVersion")
val pathCmakeVersion: ModuleDescriptor.Version? = getCMakeVersionOrNull()?.let { version ->
    runCatching { ModuleDescriptor.Version.parse(version) }.getOrElse {
        logger.error("Failed to parse version of CMake from PATH: {}", version)
        null
    }
}
logger.lifecycle("Version of CMake from PATH is $pathCmakeVersion")

fun isPathCmakeNewer(): Boolean {
    if (pathCmakeVersion == null) return false
    return pathCmakeVersion > sdkCmakeVersion
}
val useCmakeFromPath = isPathCmakeNewer()
val cmakeVersion = if (useCmakeFromPath) {
    logger.lifecycle("Using CMake from PATH")
    checkNotNull(pathCmakeVersion)
} else {
    logger.lifecycle("Using CMake from SDK")
    sdkCmakeVersion
}
val MINIMUM_CMAKE_VERSION = ModuleDescriptor.Version.parse("3.20.0")
if (cmakeVersion < MINIMUM_CMAKE_VERSION) {
    throw GradleException("CMake version ${cmakeVersion} is less than minimum version ${MINIMUM_CMAKE_VERSION}", null as Throwable?)
}

android {
    namespace = "org.equeim.libtremotesf"

    defaultConfig.externalNativeBuild.cmake.arguments(
        "-DANDROID_STL=c++_shared",
        "-DANDROID_ARM_NEON=true",
        "-DOPENSSL_DIR=${rootDir.resolve(OPENSSL_DIR)}",
        "-DQT_DIR=${rootDir.resolve(QT_DIR)}",
        // Fix CMake forcing gold linker
        "-DCMAKE_ANDROID_NDK_VERSION=${libs.versions.sdk.ndk.get().splitToSequence('.').first()}"
    )

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = cmakeVersion.toString()
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
    ndkVersion.set(android.ndkVersion)
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
    cmakeVersion.set(this@Build_gradle.cmakeVersion.toString())
    opensslInstallDirs.set(openSSL.map { it.installDirs.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
    ndkVersion.set(android.ndkVersion)
    if (!useCmakeFromPath) {
        cmakeBinaryDir.set(android.sdkDirectory.resolve("cmake/$sdkCmakeVersion/bin"))
    }
}

dependencies {
    implementation(files(QtTask.jar(rootDir)).builtBy(qt))
}

tasks.named<Delete>("clean") {
    delete(OpenSSLTask.dirsToClean(rootDir), QtTask.dirsToClean(rootDir))
}
