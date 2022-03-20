plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf)
}

dependencies {
    implementation(project(":common"))
    api(project(":libtremotesf"))

    api(libs.coroutines.core)

    api(libs.serialization.core)
    implementation(libs.serialization.json)

    implementation(libs.androidx.core)

    implementation(libs.publicsuffixlist)

    implementation(libs.timber)
}
