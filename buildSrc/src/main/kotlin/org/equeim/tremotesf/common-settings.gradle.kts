// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

package org.equeim.tremotesf

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion = JavaVersion.VERSION_17

extensions.getByName<CommonExtension>("android").configureAndroidProject()

extensions.getByName<KotlinAndroidExtension>("kotlin").compilerOptions.jvmTarget.set(
    JvmTarget.fromTarget(javaVersion.toString())
)

private fun CommonExtension.configureAndroidProject() {
    compileSdk = getSdkVersion("sdk.platform.compile")
    defaultConfig.minSdk = getSdkVersion("sdk.platform.min")
    compileOptions.apply {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    lint.apply {
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

private fun getSdkVersion(alias: String): Int = libs.findVersion(alias).get().requiredVersion.toInt()
