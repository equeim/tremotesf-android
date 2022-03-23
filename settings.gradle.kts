pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

includeBuild("gradle-plugin")
includeBuild("bencode")
include(":common", ":libtremotesf", ":rpc", ":torrentfile", ":app")
