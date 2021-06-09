package org.equeim.tremotesf.gradle

import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate

object Versions {
    const val compileSdk = 30
    const val minSdk = 16
    const val targetSdk = compileSdk
    const val ndk = "22.1.7171670"

    const val kotlinxCoroutines = "1.4.3"
    const val kotlinxSerialization = "1.2.1"

    object AndroidX {
        const val appcompat = "1.3.0"
        const val concurrentFutures = "1.1.0"
        const val core = "1.5.0"
        const val coordinatorlayout = "1.1.0"
        const val drawerlayout = "1.1.1"
        const val fragment = "1.3.4"
        const val gridlayout = "1.0.0"
        const val lifecycle = "2.3.1"
        lateinit var navigation: String
            private set
        const val recyclerview = "1.2.1"
        const val preference = "1.1.1"
        const val viewpager2 = "1.0.0"
        const val work = "2.5.0"

        internal lateinit var gradle: Gradle
            @Synchronized get

        @Synchronized
        internal fun init(gradle: Gradle) {
            if (!::navigation.isInitialized) {
                val navigation: String by (gradle as ExtensionAware).extra
                this.navigation = navigation
            }
        }
    }

    const val material = "1.3.0"
    const val fastscroll = "2.0.1"
    const val billing = "4.0.0"

    const val timber = "4.7.1"
}
