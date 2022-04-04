package org.equeim.tremotesf.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.util.*

@Suppress("UnstableApiUsage")
class GradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureAndroidProject()
        target.configureVersionsPlugin()
    }

    private fun Project.configureAndroidProject() {
        plugins.withType<AndroidBasePlugin> {
            val libs = extensions.getByType(VersionCatalogsExtension::class).named("libs")
            extensions.getByType(CommonExtension::class).configureAndroidProject(libs)
        }
        plugins.withType<KotlinAndroidPluginWrapper> {
            val androidExtension = extensions.getByType(CommonExtension::class) as ExtensionAware
            androidExtension.extensions.getByType(KotlinJvmOptions::class).configureKotlin()
        }
    }

    private fun CommonExtension<*, *, *, *>.configureAndroidProject(libs: VersionCatalog) {
        compileSdk = libs.compileSdk
        ndkVersion = libs.ndk
        defaultConfig.minSdk = libs.minSdk
        when (this) {
            is LibraryExtension -> configureAndroidProject(libs)
            is ApplicationExtension -> configureAndroidProject(libs)
        }
    }

    private fun LibraryExtension.configureAndroidProject(libs: VersionCatalog) {
        defaultConfig {
            targetSdk = libs.targetSdk
            consumerProguardFile("consumer-rules.pro")
        }
    }

    private fun ApplicationExtension.configureAndroidProject(libs: VersionCatalog) {
        defaultConfig.targetSdk = libs.targetSdk
        packagingOptions.jniLibs.useLegacyPackaging = false
    }

    private fun KotlinJvmOptions.configureKotlin() {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    private fun Project.configureVersionsPlugin() {
        if (pluginManager.hasPlugin(VERSIONS_PLUGIN_ID)) return
        pluginManager.apply(VERSIONS_PLUGIN_ID)
        tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
            val checker = DependencyVersionChecker()
            rejectVersionIf {
                checker.isNonStable(candidate.version)
            }
        }
    }

    private class DependencyVersionChecker {
        private val stableKeywords = listOf("RELEASE", "FINAL", "GA")
        private val regex = "^[0-9,.v-]+(-r)?$".toRegex()

        fun isNonStable(version: String): Boolean {
            val hasStableKeyword = stableKeywords.any { version.toUpperCase(Locale.ROOT).contains(it) }
            val isStable = hasStableKeyword || regex.matches(version)
            return isStable.not()
        }
    }

    private companion object {
        const val VERSIONS_PLUGIN_ID = "com.github.ben-manes.versions"
    }
}

private fun VersionCatalog.getVersion(alias: String): String =
    findVersion(alias).get().requiredVersion

private val VersionCatalog.compileSdk: Int
    get() = getVersion("sdk.compile").toInt()

private val VersionCatalog.minSdk: Int
    get() = getVersion("sdk.min").toInt()

private val VersionCatalog.targetSdk: Int
    get() = getVersion("sdk.target").toInt()

private val VersionCatalog.ndk: String
    get() = getVersion("ndk")
