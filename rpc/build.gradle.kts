import org.equeim.tremotesf.gradle.utils.PUBLICSUFFIXLIST_AAR_PROPERTY

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.tremotesf)
}

android.namespace = "org.equeim.tremotesf.rpc"

dependencies {
    implementation(project(":common"))
    api(project(":libtremotesf"))

    api(libs.coroutines.core)

    api(libs.serialization.core)
    implementation(libs.serialization.json)

    implementation(libs.androidx.core)

    val publicsuffixlist = providers.gradleProperty(PUBLICSUFFIXLIST_AAR_PROPERTY)
        .orNull?.takeIf { it.isNotEmpty() }?.let { aar ->
            logger.lifecycle("Using {} for publicsuffixlist dependency", aar)
            files(aar)
        } ?: libs.publicsuffixlist
    implementation(publicsuffixlist)

    implementation(libs.timber)
}
