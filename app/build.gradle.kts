import com.android.build.VariantOutput
import com.android.build.gradle.api.ApkVariantOutput
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("com.github.ben-manes.versions")
}

val abis = arrayOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
val abiVersionCodes = mapOf("armeabi-v7a" to 1, "x86" to 2, "arm64-v8a" to 3, "x86_64" to 4)

data class QtInfo(val dir: String, val jarDir: String, val hasAbiSuffix: Boolean)
val qtInfo: QtInfo by lazy {
    val qtDir = rootProject.file("3rdparty/qt")
    val jarDirNew = qtDir.resolve("install-api16/jar")
    if (jarDirNew.isDirectory) {
        QtInfo(qtDir.path, jarDirNew.path, true)
    } else {
        QtInfo(qtDir.path, qtDir.resolve("install-${abis.first()}").path, false)
    }
}

android {
    compileSdkVersion(30)
    ndkVersion = "21.3.6528147"

    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        minSdkVersion(16)
        targetSdkVersion(30)
        versionCode = 38
        versionName = "2.3.2"

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=true", "-DQT_DIR=${qtInfo.dir}", "-DQT_HAS_ABI_SUFFIX=${qtInfo.hasAbiSuffix}")
            }
        }

        buildConfigField("boolean", "QT_HAS_ABI_SUFFIX", "${qtInfo.hasAbiSuffix}")
    }

    buildTypes {
        named("release") {
            isShrinkResources = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

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

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild {
        cmake {
            path = file("../libtremotesf/CMakeLists.txt")
            version = "3.10.2"
        }
    }

    lintOptions {
        isCheckReleaseBuilds = false
    }

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

    splits {
        abi {
            isEnable = true
            reset()
            include(*abis)
            isUniversalApk = true
        }
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            val abi = (this as ApkVariantOutput).getFilter(VariantOutput.FilterType.ABI)
            val baseAbiVersionCode = abiVersionCodes[abi]
            if (baseAbiVersionCode != null) {
                versionCodeOverride = baseAbiVersionCode * 1000 + versionCode
            }
        }
    }
}

val androidxNavigation: String by (gradle as ExtensionAware).extra

object Versions {
    const val kotlinxCoroutines = "1.4.2"
    const val kotlinxSerialization = "1.0.1"

    object AndroidX {
        const val appcompat = "1.2.0"
        const val concurrentFutures = "1.1.0"
        const val core = "1.3.2"
        const val coordinatorlayout = "1.1.0"
        const val drawerlayout = "1.1.1"
        const val fragment = "1.2.5"
        const val gridlayout = "1.0.0"
        const val lifecycle = "2.3.0-rc01"
        const val recyclerview = "1.1.0"
        const val preference = "1.1.1"
        const val viewpager2 = "1.0.0"
        const val work = "2.4.0"
    }

    const val material = "1.2.1"
    const val fastscroll = "2.0.1"
    const val billing = "3.0.2"
}

dependencies {
    implementation(files("${qtInfo.jarDir}/QtAndroid.jar"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinxCoroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    implementation(project(":bencode"))

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
    implementation("androidx.navigation:navigation-fragment-ktx:$androidxNavigation")
    implementation("androidx.navigation:navigation-ui-ktx:$androidxNavigation")
    implementation("androidx.recyclerview:recyclerview:${Versions.AndroidX.recyclerview}")
    implementation("androidx.preference:preference:${Versions.AndroidX.preference}")
    implementation("androidx.viewpager2:viewpager2:${Versions.AndroidX.viewpager2}")
    implementation("androidx.work:work-runtime:${Versions.AndroidX.work}")

    implementation("com.google.android.material:material:${Versions.material}")

    implementation("com.l4digital.fastscroll:fastscroll:${Versions.fastscroll}")

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
