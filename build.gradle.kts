plugins {
    id("com.android.application") apply(false)
    kotlin("android") apply(false)
    kotlin("multiplatform") apply(false)
    id("androidx.navigation.safeargs.kotlin") apply(false)
}

val compileSdk: Int by extra(30)
val ndk: String by extra("22.0.7026061")
val minSdk: Int by extra(16)
val targetSdk: Int by extra(compileSdk)

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
