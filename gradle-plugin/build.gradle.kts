import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "org.equeim"
version = "0.1"

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
    implementation(libs.coroutines.gradle.core)
    implementation(libs.coroutines.gradle.jdk8)
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

afterEvaluate {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.6"
            languageVersion = "1.6"
        }
    }
}
