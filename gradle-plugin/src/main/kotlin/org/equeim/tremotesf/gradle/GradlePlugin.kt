package org.equeim.tremotesf.gradle

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import java.util.Locale

class GradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        Versions.AndroidX.init(target.gradle)
        target.configureVersionsPlugin()
    }

    private fun Project.configureVersionsPlugin() {
        if (pluginManager.hasPlugin(VERSIONS_PLUGIN_ID)) return
        pluginManager.apply(VERSIONS_PLUGIN_ID)
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
            val hasStableKeyword = stableKeywords.any { version.toUpperCase(Locale.ROOT).contains(it) }
            val isStable = hasStableKeyword || regex.matches(version)
            return isStable.not()
        }
    }

    private companion object {
        const val VERSIONS_PLUGIN_ID = "com.github.ben-manes.versions"
    }
}
