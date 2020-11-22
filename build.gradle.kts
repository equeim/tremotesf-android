plugins {
    id("com.android.application") apply false
    kotlin("android") apply false
    kotlin("multiplatform") apply false
}

allprojects {
    repositories {
        jcenter()
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
