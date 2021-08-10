pluginManagement {
    val android = "4.2.2"
    val kotlin = "1.5.21"
    val navigation by (gradle as ExtensionAware).extra("2.3.5")

    plugins {
        kotlin("android") version(kotlin)
        kotlin("plugin.parcelize") version(kotlin)
        kotlin("plugin.serialization") version(kotlin)
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

includeBuild("gradle-plugin")
includeBuild("bencode")
include(":common", ":libtremotesf", ":rpc", ":torrentfile", ":billing", ":app")
