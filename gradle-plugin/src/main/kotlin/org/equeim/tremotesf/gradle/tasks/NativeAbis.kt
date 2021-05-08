package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions

object NativeAbis {
    val apisToAbis = mapOf(
        Versions.minSdk to listOf("armeabi-v7a", "x86"),
        21 to listOf("arm64-v8a", "x86_64")
    )
}
