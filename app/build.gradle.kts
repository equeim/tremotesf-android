// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
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
        versionCode = 4062
        versionName = "2.13.0"
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

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability-config.conf"))
    if (findProperty("org.equeim.tremotesf.enableComposeCompilerReports") == "true") {
        reportsDestination = layout.buildDirectory.dir("composeReports")
        metricsDestination = layout.buildDirectory.dir("composeMetrics")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":torrentfile"))
    implementation(project(":rpc"))

    implementation(libs.coroutines.android)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.window)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.materialIconsCore)

    implementation(libs.composeGrid)
    implementation(libs.timber)
    implementation(libs.serialization.json.okio)
    implementation(libs.lazycolumnscrollbar)

    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.uiTooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
