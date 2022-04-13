plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("plugin") {
            id = "org.equeim.tremotesf"
            implementationClass = "org.equeim.tremotesf.gradle.GradlePlugin"
        }
    }
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.versions.plugin)
    compileOnly(libs.android.gradle.plugin.api) {
        excludeKotlinStdlib()
    }
    compileOnly(libs.kotlin.gradle.plugin) {
        excludeKotlinStdlib()
    }
    implementation(libs.coroutines15.core)
    implementation(libs.commons.text)
}

fun ModuleDependency.excludeKotlinStdlib() {
    for (module in listOf("kotlin-stdlib-common", "kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-reflect")) {
        exclude("org.jetbrains.kotlin", module)
    }
}

kotlinDslPluginOptions {
    jvmTarget.set(JavaVersion.VERSION_11.toString())
}
java.toolchain.languageVersion.set(JavaLanguageVersion.of(11))
