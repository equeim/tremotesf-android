// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class).named("libs")

private fun VersionCatalog.getVersion(alias: String): String =
    findVersion(alias).get().requiredVersion

internal val VersionCatalog.compileSdk: Int
    get() = getVersion("sdk.platform.compile").toInt()

internal val VersionCatalog.minSdk: Int
    get() = getVersion("sdk.platform.min").toInt()

internal val VersionCatalog.targetSdk: Int
    get() = getVersion("sdk.platform.target").toInt()

internal val VersionCatalog.ndk: String
    get() = getVersion("sdk.ndk")
