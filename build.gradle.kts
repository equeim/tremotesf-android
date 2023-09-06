// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
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
    delete(rootProject.layout.buildDirectory)
}
