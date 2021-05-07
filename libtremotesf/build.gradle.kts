import org.equeim.tremotesf.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
}

class QtInfo(rootProject: Project) {
    val dir = rootProject.file("3rdparty/qt")
    val jarDir: File
    val hasAbiSuffix: Boolean

    init {
        val jarDirNew = dir.resolve("install-api${Versions.minSdk}/jar")
        if (jarDirNew.isDirectory) {
            jarDir = jarDirNew
            hasAbiSuffix = true
        } else {
            jarDir = dir.resolve("install-armeabi-v7a/jar")
            hasAbiSuffix = false
        }
    }
}
val qtInfo = QtInfo(rootProject)

android {
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
        externalNativeBuild.cmake.arguments("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=true", "-DQT_DIR=${qtInfo.dir}", "-DQT_HAS_ABI_SUFFIX=${qtInfo.hasAbiSuffix}")
        buildConfigField("boolean", "QT_HAS_ABI_SUFFIX", "${qtInfo.hasAbiSuffix}")
    }

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.18.1"
    }
}

repositories {
    google()
}

dependencies {
    implementation(files(qtInfo.jarDir.resolve("QtAndroid.jar")))
}
