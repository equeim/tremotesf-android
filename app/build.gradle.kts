// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.androidx.navigation)
    alias(libs.plugins.tremotesf.common.settings)
}

class KeystoreProperties(rootProject: Project) {
    private val properties = Properties().apply {
        rootProject.file("keystore.properties").inputStream().use(::load)
    }
    val keyAlias: String by properties
    val keyPassword: String by properties
    val storeFile: String by properties
    val storePassword: String by properties
}

val keystoreProperties = try {
    KeystoreProperties(rootProject)
} catch (e: Exception) {
    null
}

android {
    namespace = "org.equeim.tremotesf"

    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        versionCode = 4059
        versionName = "2.11.1"
    }

    if (keystoreProperties != null) {
        signingConfigs.register("release") {
            keyAlias = keystoreProperties.keyAlias
            keyPassword = keystoreProperties.keyPassword
            storeFile = rootProject.file(keystoreProperties.storeFile)
            storePassword = keystoreProperties.storePassword
        }
    }

    buildTypes.named("release") {
        isShrinkResources = true
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
        signingConfig = signingConfigs.findByName("release")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions.unitTests.all { it.useJUnitPlatform() }

    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"

    // https://issuetracker.google.com/issues/306442914
    lint.disable.add("NewerVersionAvailable")

    flavorDimensions.add("freedom")
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

dependencies {
    implementation(project(":common"))
    implementation(project(":torrentfile"))
    implementation(project(":rpc"))

    implementation(libs.coroutines.android)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime)

    implementation(libs.material)
    implementation(libs.fastscroll)
    implementation(libs.timber)
    implementation(libs.serialization.json.okio)

    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
