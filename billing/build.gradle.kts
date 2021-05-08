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

    sourceSets {
        named("main") {
            java.srcDirs("src/main/kotlin")
        }
        register("google") {
            java.srcDirs("src/google/kotlin")
        }
        register("fdroid") {
            java.srcDirs("src/fdroid/kotlin")
        }
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
}

dependencies {
    implementation(project(":common"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
    "googleImplementation"("com.android.billingclient:billing-ktx:${Versions.billing}")
}
