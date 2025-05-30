// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf.common.settings)
}

android {
    namespace = "org.equeim.tremotesf.torrentfile"
    testOptions.unitTests.all { it.useJUnitPlatform() }
}

dependencies {
    implementation(project(":common"))
    api(libs.androidx.annotation)
    implementation(libs.serialization.bencode)
    api(libs.coroutines.core)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}
