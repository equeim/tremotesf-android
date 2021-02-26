pluginManagement {
    val android = "4.2.0-beta05"
    val kotlin = "1.4.31"
    val navigation by (gradle as ExtensionAware).extra("2.3.3")
    val versions = "0.36.0"

    plugins {
        kotlin("android") version(kotlin)
        kotlin("plugin.parcelize") version(kotlin)
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

include(":app", ":libtremotesf", ":bencode")
