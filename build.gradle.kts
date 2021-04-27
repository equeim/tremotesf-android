plugins {
    id("com.android.application") apply(false)
    kotlin("multiplatform") apply(false)
    kotlin("android") apply(false)
    id("androidx.navigation.safeargs.kotlin") apply(false)
}

val compileSdk: Int by extra(30)
val ndk: String by extra("22.1.7171670")
val minSdk: Int by extra(16)
val targetSdk: Int by extra(compileSdk)

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
