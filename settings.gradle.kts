pluginManagement {
    apply(from = "plugin_versions.gradle.kts")
    val extra = (gradle as ExtensionAware).extra
    val androidGradlePlugin: String by extra
    val kotlin: String by extra
    val navigation: String by extra

    plugins {
        kotlin("android") version(kotlin)
        kotlin("plugin.parcelize") version(kotlin)
        kotlin("plugin.serialization") version(kotlin)
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.namespace) {
                "com.android" -> useModule("com.android.tools.build:gradle:$androidGradlePlugin")
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
