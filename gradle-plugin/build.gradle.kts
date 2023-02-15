// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
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
    implementation(libs.serialization.gradle.json)
    implementation(libs.commons.lang)
}

fun ModuleDependency.excludeKotlinStdlib() {
    for (module in listOf("kotlin-stdlib-common", "kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-reflect")) {
        exclude("org.jetbrains.kotlin", module)
    }
}

kotlinDslPluginOptions {
    jvmTarget.set(JavaVersion.VERSION_11.toString())
}
java.toolchain.languageVersion.set(JavaLanguageVersion.of(11))

afterEvaluate {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.6"
            languageVersion = "1.6"
        }
    }
}
