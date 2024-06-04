// SPDX-FileCopyrightText: 2017-2024 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.Locale

plugins {
    alias(libs.plugins.android.application) apply (false)
    alias(libs.plugins.android.library) apply (false)
    alias(libs.plugins.kotlin.android) apply (false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply (false)
    alias(libs.plugins.kotlin.plugin.serialization) apply (false)
    alias(libs.plugins.androidx.navigation) apply (false)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.tremotesf.common.settings)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    gradleReleaseChannel = "current"
    val channelProvider = VersionChannelProvider()
    rejectVersionIf {
        val currentChannel = channelProvider.getChannel(currentVersion)
        val candidateChannel = channelProvider.getChannel(candidate.version)
        candidateChannel < currentChannel
    }
}

class VersionChannelProvider {
    enum class Channel(private val keywords: List<String>) {
        Alpha("alpha"),
        Beta("beta"),
        RC("rc"),
        Stable("release", "final", "ga");

        constructor(vararg keywords: String) : this(keywords.asList())

        fun matches(versionLowercase: String): Boolean = keywords.any { versionLowercase.contains(it) }
    }
    private val channels = Channel.values()
    private val stableVersionRegex = "^[0-9.]+$".toRegex()

    fun getChannel(version: String): Channel {
        val versionLowercase = version.lowercase(Locale.ROOT)
        if (versionLowercase.matches(stableVersionRegex)) {
            return Channel.Stable
        }
        val channelFromKeyword = channels.find { it.matches(versionLowercase) }
        if (channelFromKeyword != null) return channelFromKeyword
        throw RuntimeException("Failed to determine channel for version '$version'")
    }
}
