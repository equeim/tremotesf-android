// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

package org.equeim.tremotesf

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin

val libs = extensions.getByType(VersionCatalogsExtension::class).named("libs")
val javaVersion = JavaVersion.VERSION_11

private fun getSdkVersion(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

plugins.withType<AndroidBasePlugin> {
    val androidExtension = extensions.getByType(CommonExtension::class)
    androidExtension.configureAndroidProject()
    @Suppress("DEPRECATION")
    (androidExtension as ExtensionAware).extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions::class)
        .configureKotlin()
}

private fun CommonExtension<*, *, *, *, *, *>.configureAndroidProject() {
    compileSdk = getSdkVersion("sdk.platform.compile")
    defaultConfig.minSdk = getSdkVersion("sdk.platform.min")
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    lint {
        informational.add("MissingTranslation")
        quiet = false
        checkAllWarnings = true
        disable.addAll(listOf("InvalidPackage", "SyntheticAccessor", "TypographyQuotes"))
    }
    when (this) {
        is LibraryExtension -> configureLibraryProject()
        is ApplicationExtension -> configureApplicationProject()
        else -> Unit
    }
}

private fun LibraryExtension.configureLibraryProject() {
    defaultConfig.consumerProguardFile("consumer-rules.pro")
}

private fun ApplicationExtension.configureApplicationProject() {
    defaultConfig.targetSdk = getSdkVersion("sdk.platform.target")
}

@Suppress("DEPRECATION")
private fun org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.configureKotlin() {
    jvmTarget = javaVersion.toString()
}
