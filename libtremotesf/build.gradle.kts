import org.equeim.tremotesf.gradle.Versions
import org.equeim.tremotesf.gradle.tasks.OpenSSLTask
import org.equeim.tremotesf.gradle.tasks.PatchTask
import org.equeim.tremotesf.gradle.tasks.QtTask

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
}

val opensslDir = rootProject.file("3rdparty/openssl")
val qtDir = rootProject.file("3rdparty/qt")

android {
    defaultConfig.externalNativeBuild.cmake.arguments(
        "-DANDROID_STL=c++_shared",
        "-DANDROID_ARM_NEON=true",
        "-DOPENSSL_DIR=$opensslDir",
        "-DQT_DIR=$qtDir",
        "-DQt6CoreTools_DIR=${QtTask.coreToolsDir(qtDir)}"
    )

    buildFeatures.buildConfig = false

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.18.1"
    }
}

dependencies {
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}

val useCmakeFromSdk = (findProperty("org.equeim.tremotesf.use-cmake-from-sdk") as? String?).toBoolean()
val addHostQtCmakeFlags = (findProperty("org.equeim.tremotesf.host-qt-cmake-flags") as? String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
val useCcache = (findProperty("org.equeim.tremotesf.ccache") as? String?).toBoolean()

val openSSLPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(OpenSSLTask.sourceDir(opensslDir))
    patchesDir.set(OpenSSLTask.patchesDir(opensslDir))
}

val openSSL by tasks.registering(OpenSSLTask::class) {
    dependsOn(openSSLPatches)
    opensslDir.set(this@Build_gradle.opensslDir)
    ndkDir.set(android.ndkDirectory)
    ccache.set(useCcache)
}

val qtPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(QtTask.sourceDir(qtDir))
    patchesDir.set(QtTask.patchesDir(qtDir))
    substitutionMap.put(Versions::compileSdk.name, Versions.compileSdk)
}

val qt by tasks.registering(QtTask::class) {
    dependsOn(qtPatches)
    qtDir.set(this@Build_gradle.qtDir)
    opensslInstallDirs.set(openSSL.map { it.installDirs.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
    if (useCmakeFromSdk) {
        cmakeBinaryDir.set(android.sdkDirectory.resolve("cmake/${android.externalNativeBuild.cmake.version}/bin"))
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
