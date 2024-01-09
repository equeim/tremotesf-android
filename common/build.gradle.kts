// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.tremotesf)
}

android.namespace = "org.equeim.tremotesf.common"

dependencies {
    api(libs.coroutines.core)
}
