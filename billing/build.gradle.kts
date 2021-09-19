import org.equeim.tremotesf.gradle.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
    kotlin("android")
}


android {
    compileSdk = Versions.compileSdk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
    }

    sourceSets.named("main") {
        java.srcDirs("src/main/kotlin")
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

repositories {
    mavenCentral()
    google()
}
