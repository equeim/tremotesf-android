pluginManagement {
    val android = "4.2.1"
    val kotlin = "1.4.32"
    val navigation by (gradle as ExtensionAware).extra("2.3.5")
    val versions = "0.38.0"

    plugins {
        kotlin("android") version(kotlin)
        kotlin("plugin.parcelize") version(kotlin)
        kotlin("plugin.serialization") version(kotlin)
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

includeBuild("bencode")
include(":app", ":libtremotesf")
