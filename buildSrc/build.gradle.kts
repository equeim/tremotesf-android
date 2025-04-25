// SPDX-FileCopyrightText: 2017-2025 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

val javaVersion = JavaVersion.VERSION_21
tasks.withType<JavaCompile> {
    options.release.set(javaVersion.majorVersion.toInt())
}
tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
}

dependencies {
    implementation(libs.android.gradle.plugin.api)
    implementation(libs.kotlin.gradle.plugin.api)
}
