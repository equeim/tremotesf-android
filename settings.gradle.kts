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
        id("com.android.application") version(androidGradlePlugin)
        id("com.android.library") version(androidGradlePlugin)
        id("androidx.navigation.safeargs.kotlin") version(navigation)
    }
    repositories {
        gradlePluginPortal()
        google()
    }
}

includeBuild("gradle-plugin")
includeBuild("bencode")
include(":common", ":libtremotesf", ":rpc", ":torrentfile", ":billing", ":app")
