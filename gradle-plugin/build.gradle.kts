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

apply(from = "../plugin_versions.gradle.kts")
val extra = (gradle as ExtensionAware).extra
val gradleVersionsPlugin: String by extra
val androidGradlePlugin: String by extra
val kotlin: String by extra

val commonsText = "1.9"

dependencies {
    implementation("com.github.ben-manes:gradle-versions-plugin:$gradleVersionsPlugin")
    compileOnly("com.android.tools.build:gradle-api:$androidGradlePlugin") {
        excludeKotlinStdlib()
    }
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin") {
        excludeKotlinStdlib()
    }
    implementation("org.apache.commons:commons-text:$commonsText")
}

fun ModuleDependency.excludeKotlinStdlib() {
    for (module in listOf("kotlin-stdlib-common", "kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-reflect")) {
        exclude("org.jetbrains.kotlin", module)
    }
}

kotlinDslPluginOptions {
    jvmTarget.set(JavaVersion.VERSION_11.toString())
}
