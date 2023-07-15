// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf)
}

android {
    namespace = "org.equeim.tremotesf.torrentfile"
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    testOptions.unitTests.all { it.useJUnitPlatform() }
}

dependencies {
    implementation(project(":common"))
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
