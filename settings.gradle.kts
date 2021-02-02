pluginManagement {
    val android = "4.1.2"
    val kotlin = "1.4.21"
    val navigation by (gradle as ExtensionAware).extra("2.3.2")
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
            when (requested.id.namespace) {
                "com.android" -> useModule("com.android.tools.build:gradle:$android")
                "androidx.navigation.safeargs" -> useModule("androidx.navigation:navigation-safe-args-gradle-plugin:$navigation")
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
