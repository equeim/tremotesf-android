// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf.common.settings)
}

android {
    namespace = "org.equeim.tremotesf.rpc"
    testOptions.unitTests.all { it.useJUnitPlatform() }
}

dependencies {
    implementation(project(":common"))

    api(libs.androidx.annotation)
    api(libs.coroutines.core)

    api(libs.serialization.core)
    implementation(libs.serialization.json)

    implementation(libs.androidx.core)

    implementation(libs.timber)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json.okio)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
}
