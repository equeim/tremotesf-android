plugins {
    id("com.android.library")
}

class Versions(rootProject: Project) {
    val compileSdk: Int by rootProject.extra
    val ndk: String by rootProject.extra
    val minSdk: Int by rootProject.extra
    val targetSdk: Int by rootProject.extra
}
val vers = Versions(rootProject)


class QtInfo(rootProject: Project) {
    val dir = rootProject.file("3rdparty/qt")
    val jarDir: File
    val hasAbiSuffix: Boolean

    init {
        val jarDirNew = dir.resolve("install-api${vers.minSdk}/jar")
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
    compileSdk = vers.compileSdk
    ndkVersion = vers.ndk

    defaultConfig {
        minSdk = vers.minSdk
        targetSdk = vers.targetSdk
        consumerProguardFile("consumer-rules.pro")
        externalNativeBuild.cmake.arguments("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=true", "-DQT_DIR=${qtInfo.dir}", "-DQT_HAS_ABI_SUFFIX=${qtInfo.hasAbiSuffix}")
        buildConfigField("boolean", "QT_HAS_ABI_SUFFIX", "${qtInfo.hasAbiSuffix}")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
