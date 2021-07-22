import org.equeim.tremotesf.gradle.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
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
    
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    flavorDimensions("freedom")
    productFlavors {
        register("google") {
            dimension = "freedom"
            buildConfigField("boolean", "GOOGLE", "true")
        }
        register("fdroid") {
            dimension = "freedom"
            buildConfigField("boolean", "GOOGLE", "false")
        }
    }
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.mozilla.org/maven2")
}

dependencies {
    implementation(project(":common"))
    api(project(":libtremotesf"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinxSerialization}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

    implementation("androidx.core:core-ktx:${Versions.AndroidX.core}")

    implementation("org.mozilla.components:lib-publicsuffixlist:${Versions.publicsuffixlist}")

    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}
