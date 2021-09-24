import org.equeim.tremotesf.gradle.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android.buildFeatures.buildConfig = false

dependencies {
    implementation(project(":common"))
    implementation("org.equeim:kotlinx-serialization-bencode")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
    implementation("androidx.collection:collection-ktx:${Versions.AndroidX.collection}")
    implementation("androidx.core:core-ktx:${Versions.AndroidX.core}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${Versions.AndroidX.lifecycle}")
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
    testImplementation("junit:junit:${Versions.junit}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
}
