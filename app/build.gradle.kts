import java.util.Properties
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("com.github.ben-manes.versions")
}

class Versions(rootProject: Project) {
    val compileSdk: Int by rootProject.extra
    val ndk: String by rootProject.extra
    val minSdk: Int by rootProject.extra
    val targetSdk: Int by rootProject.extra

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
        val work = "2.5.0"
    }
    val androidX = AndroidX(rootProject.gradle)

    val material = "1.3.0"
    val fastscroll = "2.0.1"
    val billing = "3.0.2"
}
val vers = Versions(rootProject)

class KeystoreProperties(rootProject: Project) {
    private val properties = Properties().apply {
        load(rootProject.file("keystore.properties").inputStream())
    }
    val keyAlias: String by properties
    val keyPassword: String by properties
    val storeFile: String by properties
    val storePassword: String by properties
}
val keystoreProperties = KeystoreProperties(rootProject)

android {
    compileSdk = vers.compileSdk
    ndkVersion = vers.ndk

    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        minSdk = vers.minSdk
        targetSdk = vers.targetSdk
        versionCode = 4038
        versionName = "2.3.2"
    }

    signingConfigs.register("release") {
        keyAlias = keystoreProperties.keyAlias
        keyPassword = keystoreProperties.keyPassword
        storeFile = rootProject.file(keystoreProperties.storeFile)
        storePassword = keystoreProperties.storePassword
    }

    buildTypes.named("release") {
        isShrinkResources = false
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))

        signingConfig = signingConfigs["release"]
    }

    buildFeatures.viewBinding = true

    sourceSets {
        named("main") {
            java.srcDirs("src/main/kotlin")
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

repositories {
    jcenter()
    google()
    mavenCentral()
}

dependencies {
    implementation(project(":libtremotesf"))
    implementation(project(":bencode"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${vers.kotlinxCoroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${vers.kotlinxSerialization}")

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
