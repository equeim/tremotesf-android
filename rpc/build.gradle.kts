import org.equeim.tremotesf.gradle.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":common"))
    api(project(":libtremotesf"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinxSerialization}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

    implementation("androidx.core:core-ktx:${Versions.AndroidX.core}")

    implementation("org.mozilla.components:lib-publicsuffixlist:${Versions.publicsuffixlist}")

    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}
