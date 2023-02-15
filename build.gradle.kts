// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.application) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.kotlin.android) apply(false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply(false)
    alias(libs.plugins.kotlin.plugin.serialization) apply(false)
    alias(libs.plugins.androidx.navigation) apply(false)
    alias(libs.plugins.tremotesf)
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

/**
 * Workaround to set up vcpkg when Android Studio runs project sync
 * In that case Gradle invokes CMake without running configureCMake tasks, and task dependencies set up
 * in libtremotesf/build.gradle.kts don't work
 */
tasks.named("prepareKotlinBuildScriptModel") {
    val dep = ":libtremotesf:runVcpkgInstallTask"
    logger.lifecycle("Altering $this to depend on $dep")
    dependsOn(dep)
}
