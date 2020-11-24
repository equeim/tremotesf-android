pluginManagement {
    val android = "4.0.2"
    val kotlin = "1.4.20"
    val versions = "0.36.0"

    plugins {
        kotlin("android") version(kotlin)
        kotlin("android.extensions") version(kotlin)
        kotlin("plugin.serialization") version(kotlin)
        kotlin("multiplatform") version(kotlin)
        id("com.github.ben-manes.versions") version(versions)
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:$android")
            }
        }
    }
    repositories {
        gradlePluginPortal()
        google()
    }
}

include(":app")
include(":bencode")
