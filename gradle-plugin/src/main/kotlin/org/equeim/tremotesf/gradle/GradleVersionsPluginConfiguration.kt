// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import java.util.*

private const val GRADLE_VERSIONS_PLUGIN_ID = "com.github.ben-manes.versions"

fun Project.configureGradleVersionsPlugin() {
    // Apply only to root project
    if (this != rootProject) return
    if (pluginManager.hasPlugin(GRADLE_VERSIONS_PLUGIN_ID)) return
    pluginManager.apply(GRADLE_VERSIONS_PLUGIN_ID)
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
        val versionUppercase = version.uppercase(Locale.ROOT)
        val hasStableKeyword = stableKeywords.any(versionUppercase::contains)
        val isStable = hasStableKeyword || regex.matches(version)
        return isStable.not()
    }
}
