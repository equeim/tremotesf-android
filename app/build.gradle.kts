import java.util.Properties
import org.equeim.tremotesf.gradle.Versions

plugins {
    id("org.equeim.tremotesf")
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.parcelize")
    id("androidx.navigation.safeargs.kotlin")
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
    defaultConfig {
        applicationId = "org.equeim.tremotesf"
        versionCode = 4046
        versionName = "2.5.4"

        vectorDrawables.useSupportLibrary = true
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

        ndk.debugSymbolLevel = "FULL"
    }

    buildFeatures.viewBinding = true

    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"

    lint.informational.add("MissingTranslation")

    flavorDimensions.add("freedom")
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

androidComponents {
    onVariants(selector().withBuildType("debug")) {
        it.packaging.jniLibs.keepDebugSymbols.add("**/*.so")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":billing"))
    implementation(project(":torrentfile"))
    implementation(project(":rpc"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinxCoroutines}")

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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-service:${Versions.AndroidX.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-common-java8:${Versions.AndroidX.lifecycle}")
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.AndroidX.navigation}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.AndroidX.navigation}")
    implementation("androidx.recyclerview:recyclerview:${Versions.AndroidX.recyclerview}")
    implementation("androidx.preference:preference:${Versions.AndroidX.preference}")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:${Versions.AndroidX.swipeRefreshLayout}")
    implementation("androidx.viewpager2:viewpager2:${Versions.AndroidX.viewpager2}")
    implementation("androidx.work:work-runtime:${Versions.AndroidX.work}")

    implementation("com.google.android.material:material:${Versions.material}")

    implementation("io.github.l4digital:fastscroll:${Versions.fastscroll}")

    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}
