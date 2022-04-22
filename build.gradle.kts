plugins {
    alias(libs.plugins.android.application) apply(false)
    alias(libs.plugins.android.library) apply(false)
    alias(libs.plugins.kotlin.android) apply(false)
    alias(libs.plugins.kotlin.plugin.parcelize) apply(false)
    alias(libs.plugins.kotlin.plugin.serialization) apply(false)
    alias(libs.plugins.androidx.navigation) apply(false)
    alias(libs.plugins.tremotesf)
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
