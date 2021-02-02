import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("com.github.ben-manes.versions")
}

data class QtInfo(val dir: String, val jarDir: String, val hasAbiSuffix: Boolean)
val qtInfo: QtInfo by lazy {
    val qtDir = rootProject.file("3rdparty/qt")
    val jarDirNew = qtDir.resolve("install-api16/jar")
    if (jarDirNew.isDirectory) {
        QtInfo(qtDir.path, jarDirNew.path, true)
    } else {
        QtInfo(qtDir.path, qtDir.resolve("install-armeabi-v7a").path, false)
    }
}

android {
    compileSdk = 30
    ndkVersion = "21.3.6528147"

    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        minSdk = 16
        targetSdk = 30
        versionCode = 4036
        versionName = "2.3.0"

        externalNativeBuild.cmake.arguments("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=true", "-DQT_DIR=${qtInfo.dir}", "-DQT_HAS_ABI_SUFFIX=${qtInfo.hasAbiSuffix}")

        buildConfigField("boolean", "QT_HAS_ABI_SUFFIX", "${qtInfo.hasAbiSuffix}")
    }

    buildTypes.named("release") {
        isShrinkResources = true
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))
    }

    buildFeatures.viewBinding = true

    sourceSets {
        named("main") {
            java.srcDirs("src/main/kotlin", "../libtremotesf/jni/java")
        }
        register("google") {
            java.srcDirs("src/google/kotlin")
        }
        register("fdroid") {
            java.srcDirs("src/fdroid/kotlin")
        }
    }

    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild.cmake {
        path = file("../libtremotesf/CMakeLists.txt")
        version = "3.10.2"
    }

    lintOptions.isCheckReleaseBuilds = false

    flavorDimensions("freedom")
    productFlavors {
        register("google") {
            dimension = "freedom"
            buildConfigField("boolean", "DONATIONS_GOOGLE", "true")
        }
        register("fdroid") {
            dimension = "freedom"
            buildConfigField("boolean", "DONATIONS_GOOGLE", "false")
        }
    }
}

class Versions(gradle: Gradle) {
    val kotlinxCoroutines = "1.4.2"
    val kotlinxSerialization = "1.0.1"

    class AndroidX(gradle: Gradle) {
        val appcompat = "1.2.0"
        val concurrentFutures = "1.1.0"
        val core = "1.3.2"
        val coordinatorlayout = "1.1.0"
        val drawerlayout = "1.1.1"
        val fragment = "1.2.5"
        val gridlayout = "1.0.0"
        val lifecycle = "2.3.0-rc01"
        val navigation: String by (gradle as ExtensionAware).extra
        val recyclerview = "1.1.0"
        val preference = "1.1.1"
        val viewpager2 = "1.0.0"
        val work = "2.4.0"
    }
    val androidX = AndroidX(gradle)

    val material = "1.2.1"
    val fastscroll = "2.0.1"
    val billing = "3.0.2"
}
val vers = Versions(gradle)

dependencies {
    implementation(files("${qtInfo.jarDir}/QtAndroid.jar"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${vers.kotlinxCoroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${vers.kotlinxSerialization}")
    implementation(project(":bencode"))

    implementation("androidx.appcompat:appcompat:${vers.androidX.appcompat}")
    implementation("androidx.concurrent:concurrent-futures:${vers.androidX.concurrentFutures}")
    implementation("androidx.core:core:${vers.androidX.core}")
    implementation("androidx.core:core-ktx:${vers.androidX.core}")
    implementation("androidx.coordinatorlayout:coordinatorlayout:${vers.androidX.coordinatorlayout}")
    implementation("androidx.drawerlayout:drawerlayout:${vers.androidX.drawerlayout}")
    implementation("androidx.fragment:fragment:${vers.androidX.fragment}")
    implementation("androidx.fragment:fragment-ktx:${vers.androidX.fragment}")
    implementation("androidx.gridlayout:gridlayout:${vers.androidX.gridlayout}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${vers.androidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${vers.androidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${vers.androidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-service:${vers.androidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-common-java8:${vers.androidX.lifecycle}")
    implementation("androidx.navigation:navigation-fragment-ktx:${vers.androidX.navigation}")
    implementation("androidx.navigation:navigation-ui-ktx:${vers.androidX.navigation}")
    implementation("androidx.recyclerview:recyclerview:${vers.androidX.recyclerview}")
    implementation("androidx.preference:preference:${vers.androidX.preference}")
    implementation("androidx.viewpager2:viewpager2:${vers.androidX.viewpager2}")
    implementation("androidx.work:work-runtime:${vers.androidX.work}")

    implementation("com.google.android.material:material:${vers.material}")

    implementation("com.l4digital.fastscroll:fastscroll:${vers.fastscroll}")

    "googleImplementation"("com.android.billingclient:billing-ktx:${vers.billing}")
}

object DependencyVersionChecker {
    private val stableKeywords = listOf("RELEASE", "FINAL", "GA")
    private val regex = "^[0-9,.v-]+(-r)?$".toRegex()

    fun isNonStable(version: String): Boolean {
        val hasStableKeyword = stableKeywords.any { version.toUpperCase().contains(it) }
        val isStable = hasStableKeyword || regex.matches(version)
        return isStable.not()
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    rejectVersionIf {
        DependencyVersionChecker.isNonStable(candidate.version)
    }
}
