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
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
        externalNativeBuild.cmake.arguments(
            "-DANDROID_STL=c++_shared",
            "-DANDROID_ARM_NEON=true",
            "-DQT_DIR=$qtDir",
            "-DQt6CoreTools_DIR=${QtTask.coreToolsDir(qtDir)}"
        )
    }

    buildFeatures.buildConfig = false

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.18.1"
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}

val useCcache = (property("org.equeim.tremotesf.ccache") as String).toBoolean()

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
}

val qt by tasks.registering(QtTask::class) {
    dependsOn(qtPatches)
    qtDir.set(this@Build_gradle.qtDir)
    opensslInstallDirs.set(openSSL.map { it.installDirs.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
    ccache.set(useCcache)
}

dependencies {
    implementation(files(QtTask.jar(qtDir)).builtBy(qt))
}

val clean3rdparty by tasks.registering(Delete::class) {
    delete(OpenSSLTask.dirsToClean(opensslDir), QtTask.dirsToClean(qtDir))
}
