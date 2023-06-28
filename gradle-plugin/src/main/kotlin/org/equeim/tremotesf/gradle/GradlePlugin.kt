// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.equeim.tremotesf.gradle.utils.*
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

class GradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureAndroidProject()
        target.configureGradleVersionsPlugin()
    }

    private fun Project.configureAndroidProject() {
        plugins.withType<AndroidBasePlugin> {
            extensions.getByType(CommonExtension::class).configureAndroidProject(libs)
        }
        plugins.withType<KotlinAndroidPluginWrapper> {
            val androidExtension = extensions.getByType(CommonExtension::class) as ExtensionAware
            androidExtension.extensions.getByType(KotlinJvmOptions::class).configureKotlin()
        }
    }

    private fun CommonExtension<*, *, *, *>.configureAndroidProject(libs: VersionCatalog) {
        compileSdk = libs.compileSdk
        @Suppress("UnstableApiUsage")
        ndkVersion = libs.ndk
        defaultConfig.minSdk = libs.minSdk
        lint.apply {
            informational.add("MissingTranslation")
            quiet = false
            checkAllWarnings = true
            disable.addAll(listOf("InvalidPackage", "SyntheticAccessor", "TypographyQuotes"))
        }
        when (this) {
            is LibraryExtension -> configureLibraryProject()
            is ApplicationExtension -> configureApplicationProject(libs)
        }
    }

    private fun LibraryExtension.configureLibraryProject() {
        defaultConfig.consumerProguardFile("consumer-rules.pro")
    }

    private fun ApplicationExtension.configureApplicationProject(libs: VersionCatalog) {
        defaultConfig.targetSdk = libs.targetSdk
        packaging.jniLibs.useLegacyPackaging = false
    }

    private fun KotlinJvmOptions.configureKotlin() {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
