// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: CC0-1.0

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("org.jetbrains.kotlin.plugin.serialization") version embeddedKotlinVersion
}

group = "org.equeim"
version = "0.1"

gradlePlugin {
    plugins {
        create("plugin") {
            id = "org.equeim.tremotesf"
            implementationClass = "org.equeim.tremotesf.gradle.GradlePlugin"
        }
    }
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.versions.plugin)
    compileOnly(libs.android.gradle.plugin.api) {
        excludeKotlinStdlib()
    }
    compileOnly(libs.kotlin.gradle.plugin) {
        excludeKotlinStdlib()
    }
    implementation(libs.serialization.gradle.json) {
        excludeKotlinStdlib()
    }
    implementation(libs.commons.lang)
}

fun ModuleDependency.excludeKotlinStdlib() {
    for (module in listOf(
        "kotlin-stdlib-common",
        "kotlin-stdlib",
        "kotlin-stdlib-jdk7",
        "kotlin-stdlib-jdk8",
        "kotlin-reflect"
    )) {
        exclude("org.jetbrains.kotlin", module)
    }
}

val javaVersion = JavaVersion.VERSION_11
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = javaVersion.toString()
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion.majorVersion.toInt())
    sourceCompatibility = javaVersion.toString()
    targetCompatibility = javaVersion.toString()
}
