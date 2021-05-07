import java.util.Properties
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.equeim.tremotesf.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("com.github.ben-manes.versions")
}

class KeystoreProperties(rootProject: Project) {
    private val properties = Properties().apply {
        load(rootProject.file("keystore.properties").inputStream())
    }
    val keyAlias: String by properties
    val keyPassword: String by properties
    val storeFile: String by properties
    val storePassword: String by properties
}
val keystoreProperties = try {
    KeystoreProperties(rootProject)
} catch (e: Exception) {
    null
}

android {
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionCode = 4041
        versionName = "2.4.2"
    }

    if (keystoreProperties != null) {
        signingConfigs.register("release") {
            keyAlias = keystoreProperties.keyAlias
            keyPassword = keystoreProperties.keyPassword
            storeFile = rootProject.file(keystoreProperties.storeFile)
            storePassword = keystoreProperties.storePassword
        }
    }

    buildTypes.named("release") {
        isShrinkResources = true
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro"))

        signingConfig = signingConfigs.findByName("release")
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

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        useIR = true
    }

    lintOptions.isCheckReleaseBuilds = false

    flavorDimensions("freedom")
    productFlavors {
        register("google") {
            dimension = "freedom"
            buildConfigField("boolean", "GOOGLE", "true")
        }
        register("fdroid") {
            dimension = "freedom"
            buildConfigField("boolean", "GOOGLE", "false")
        }
    }
}

repositories {
    mavenCentral()
    google()
    jcenter {
        content {
            includeModule("com.l4digital.fastscroll", "fastscroll")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":rpc"))
    implementation("org.equeim:kotlinx-serialization-bencode")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinxCoroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

    implementation("androidx.appcompat:appcompat:${Versions.AndroidX.appcompat}")
    implementation("androidx.concurrent:concurrent-futures:${Versions.AndroidX.concurrentFutures}")
    implementation("androidx.core:core:${Versions.AndroidX.core}")
    implementation("androidx.core:core-ktx:${Versions.AndroidX.core}")
    implementation("androidx.coordinatorlayout:coordinatorlayout:${Versions.AndroidX.coordinatorlayout}")
    implementation("androidx.drawerlayout:drawerlayout:${Versions.AndroidX.drawerlayout}")
    implementation("androidx.fragment:fragment:${Versions.AndroidX.fragment}")
    implementation("androidx.fragment:fragment-ktx:${Versions.AndroidX.fragment}")
    implementation("androidx.gridlayout:gridlayout:${Versions.AndroidX.gridlayout}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-service:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-common-java8:${Versions.AndroidX.lifecycle}")
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.navigation}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.navigation}")
    implementation("androidx.recyclerview:recyclerview:${Versions.AndroidX.recyclerview}")
    implementation("androidx.preference:preference:${Versions.AndroidX.preference}")
    implementation("androidx.viewpager2:viewpager2:${Versions.AndroidX.viewpager2}")
    implementation("androidx.work:work-runtime:${Versions.AndroidX.work}")

    implementation("com.google.android.material:material:${Versions.material}")

    implementation("com.l4digital.fastscroll:fastscroll:${Versions.fastscroll}")

    implementation("com.jakewharton.timber:timber:${Versions.timber}")

    "googleImplementation"("com.android.billingclient:billing-ktx:${Versions.billing}")
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
