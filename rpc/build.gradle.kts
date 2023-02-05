// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf)
}

android.namespace = "org.equeim.tremotesf.rpc"

dependencies {
    implementation(project(":common"))
    api(project(":libtremotesf"))

    api(libs.coroutines.core)

    api(libs.serialization.core)
    implementation(libs.serialization.json)

    implementation(libs.androidx.core)

    implementation(libs.publicsuffixlist)

    implementation(libs.timber)
}
