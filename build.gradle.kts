import com.deezer.caupain.plugin.DependenciesUpdateTask

// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.kotlin.plugin.compose) apply (false)
    alias(libs.plugins.kotlin.plugin.serialization) apply (false)
    alias(libs.plugins.androidx.navigation) apply (false)
    alias(libs.plugins.tremotesf.common.settings) apply(false)
    alias(libs.plugins.deezer.caupain)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
