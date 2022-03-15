package org.equeim.tremotesf.gradle

import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.extra
import java.util.concurrent.atomic.AtomicReference

object Versions {
    const val compileSdk = 31
    const val minSdk = 21
    const val targetSdk = compileSdk
    const val ndk = "23.0.7599858"

    const val kotlinxCoroutines = "1.6.0"
    const val kotlinxSerialization = "1.3.2"

    object AndroidX {
        const val appcompat = "1.4.1"
        const val collection = "1.2.0"
        const val concurrentFutures = "1.1.0"
        const val core = "1.7.0"
        const val coordinatorlayout = "1.2.0"
        const val drawerlayout = "1.1.1"
        const val fragment = "1.4.1"
        const val gridlayout = "1.0.0"
        const val lifecycle = "2.4.1"

        private val _navigation = AtomicReference<String>()
        val navigation: String
            get() = checkNotNull(_navigation.get())

        const val recyclerview = "1.2.1"
        const val preference = "1.2.0"
        const val swipeRefreshLayout = "1.1.0"
        const val viewpager2 = "1.0.0"
        const val work = "2.7.1"

        internal fun init(gradle: Gradle) {
            _navigation.updateAndGet { it ?: (gradle as ExtensionAware).extra["navigation"] as String }
        }
    }

    const val material = "1.5.0"
    const val fastscroll = "2.1.0"

    const val timber = "5.0.1"

    const val junit = "4.13.2"

    const val publicsuffixlist = "99.0.2"
}
