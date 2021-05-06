import org.equeim.tremotesf.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
    kotlin("android")
}


android {
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
    }

    sourceSets.named("main") {
        java.srcDirs("src/main/kotlin")
    }

    buildFeatures.buildConfig = false

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        useIR = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
}
