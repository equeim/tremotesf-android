plugins {
    id("com.android.application") apply(false)
    kotlin("android") apply(false)
    id("androidx.navigation.safeargs.kotlin") apply(false)
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
