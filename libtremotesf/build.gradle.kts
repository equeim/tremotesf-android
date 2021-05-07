import org.equeim.tremotesf.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
}

val qtDir = rootProject.file("3rdparty/qt")

android {
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
        externalNativeBuild.cmake.arguments("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=true", "-DQT_DIR=$qtDir")
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
    implementation(files(qtDir.resolve("install-api${Versions.minSdk}/jar/QtAndroid.jar")))
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}
