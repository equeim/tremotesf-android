// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

package org.equeim.tremotesf

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

val libs = extensions.getByType(VersionCatalogsExtension::class).named("libs")
val javaVersion = JavaVersion.VERSION_17

private fun getSdkVersion(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

typealias AndroidExtension = CommonExtension<*, *, *, *, *, *>

plugins.withType<AndroidBasePlugin> {
    extensions.getByName<AndroidExtension>("android").configureAndroidProject()
}

plugins.withType<KotlinBasePlugin> {
    extensions.getByName<KotlinAndroidExtension>("kotlin").compilerOptions.jvmTarget.set(
        JvmTarget.fromTarget(javaVersion.toString())
    )
}

private fun AndroidExtension.configureAndroidProject() {
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
