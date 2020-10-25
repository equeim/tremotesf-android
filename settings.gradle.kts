pluginManagement {
    val android = "4.0.2"
    val kotlin = "1.4.10"

    plugins {
        id("com.android.application") version(android)
        kotlin("android") version(kotlin)
        kotlin("android.extensions") version(kotlin)
        kotlin("plugin.serialization") version(kotlin)
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
