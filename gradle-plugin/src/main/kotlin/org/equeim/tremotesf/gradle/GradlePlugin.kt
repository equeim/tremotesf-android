package org.equeim.tremotesf.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        Versions.AndroidX.init(target.gradle)
    }
}
