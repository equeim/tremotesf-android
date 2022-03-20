plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.tremotesf)
}

android.buildFeatures.buildConfig = false

dependencies {
    api(libs.coroutines.core)
}
