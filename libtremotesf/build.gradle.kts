import java.lang.module.ModuleDescriptor
import org.equeim.tremotesf.gradle.tasks.OpenSSLTask
import org.equeim.tremotesf.gradle.tasks.PatchTask
import org.equeim.tremotesf.gradle.tasks.QtTask
import org.equeim.tremotesf.gradle.utils.getCMakeVersionOrNull

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.tremotesf)
}

val opensslDir = rootProject.file("3rdparty/openssl")
val qtDir = rootProject.file("3rdparty/qt")

val sdkCmakeVersion = "3.18.1"
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
        "-DOPENSSL_DIR=$opensslDir",
        "-DQT_DIR=$qtDir"
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

val addHostQtCmakeFlags = (findProperty("org.equeim.tremotesf.host-qt-cmake-flags") as? String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
val useCcache = (findProperty("org.equeim.tremotesf.ccache") as? String?).toBoolean()

val openSSLPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(OpenSSLTask.sourceDir(opensslDir))
    patchesDir.set(OpenSSLTask.patchesDir(opensslDir))
}

val openSSL by tasks.registering(OpenSSLTask::class) {
    dependsOn(openSSLPatches)
    minSdkVersion.set(libs.versions.sdk.min)
    opensslDir.set(this@Build_gradle.opensslDir)
    ndkDir.set(android.ndkDirectory)
    ccache.set(useCcache)
}

val qtPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(QtTask.sourceDir(qtDir))
    patchesDir.set(QtTask.patchesDir(qtDir))
    substitutionMap.put("compileSdk", libs.versions.sdk.compile)
}

val qt by tasks.registering(QtTask::class) {
    dependsOn(qtPatches)
    minSdkVersion.set(libs.versions.sdk.min)
    qtDir.set(this@Build_gradle.qtDir)
    opensslInstallDirs.set(openSSL.map { it.installDirs.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
    cmakeVersion.set(this@Build_gradle.cmakeVersion)
    if (!useCmakeFromPath) {
        cmakeBinaryDir.set(android.sdkDirectory.resolve("cmake/$sdkCmakeVersion/bin"))
    }
    hostQtCmakeFlags.set(addHostQtCmakeFlags)
    ccache.set(useCcache)
}

dependencies {
    implementation(files(QtTask.jar(qtDir)).builtBy(qt))
}

val clean3rdparty by tasks.registering(Delete::class) {
    delete(OpenSSLTask.dirsToClean(opensslDir), QtTask.dirsToClean(qtDir))
}
