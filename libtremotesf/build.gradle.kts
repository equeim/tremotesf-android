import org.equeim.tremotesf.gradle.Versions
import org.equeim.tremotesf.gradle.tasks.OpenSSLTask
import org.equeim.tremotesf.gradle.tasks.PatchTask
import org.equeim.tremotesf.gradle.tasks.QtTask

plugins {
    id("org.equeim.tremotesf")
    id("com.android.library")
}

val opensslDir = rootProject.file("3rdparty/openssl")
val qtDir = rootProject.file("3rdparty/qt")

val generatedCppSourceBaseDir = buildDir.resolve("generated/source/javacpp-cpp")

android {
    compileSdk = Versions.compileSdk
    ndkVersion = Versions.ndk

    defaultConfig {
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        consumerProguardFile("consumer-rules.pro")
        externalNativeBuild.cmake.arguments(
            "-DANDROID_STL=c++_shared",
            "-DANDROID_ARM_NEON=true",
            "-DQT_DIR=$qtDir",
            "-DGENERATED_SOURCES_BASE_DIR=${generatedCppSourceBaseDir}"
        )
    }

    buildFeatures.buildConfig = false

    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.18.1"
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.jakewharton.timber:timber:${Versions.timber}")
}

val openSSLPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(opensslDir.resolve(OpenSSLTask.SOURCE_DIR))
    patchesDir.set(opensslDir.resolve(OpenSSLTask.PATCHES_DIR))
}

val openSSL by tasks.registering(OpenSSLTask::class) {
    dependsOn(openSSLPatches)
    opensslDir.set(this@Build_gradle.opensslDir)
    ndkDir.set(android.ndkDirectory)
}

val qtPatches by tasks.registering(PatchTask::class) {
    sourceDir.set(rootProject.file(qtDir.resolve(QtTask.SOURCE_DIR)))
    patchesDir.set(rootProject.file(qtDir.resolve(QtTask.PATCHES_DIR)))
}

val qt by tasks.registering(QtTask::class) {
    dependsOn(qtPatches)
    qtDir.set(this@Build_gradle.qtDir)
    opensslInstallDir.set(openSSL.map { it.installDir.get() })
    sdkDir.set(android.sdkDirectory)
    ndkDir.set(android.ndkDirectory)
}

val prepareNativeSources by tasks.registering(Task::class) {
    dependsOn(qt)
}

val clean3rdparty by tasks.registering(Delete::class) {
    delete(OpenSSLTask.buildDirs(opensslDir), opensslDir.resolve(OpenSSLTask.INSTALL_DIR))
    delete(QtTask.buildDirs(qtDir), QtTask.installPrefixes(qtDir))
}

val clean = tasks.named<Delete>("clean")

val javacppConfiguration = configurations.create("javacpp")

android.libraryVariants.configureEach {
    val variantName = name.capitalize()

    val javaSourceDirs = android.sourceSets.getByName("main").java.srcDirs
    val generatedJavaSourceDir = buildDir.resolve("generated/source/javacpp/$dirName")

    val cppSourceDir = file("src/main/cpp")
    val generatedCppSourceDir = buildDir.resolve("generated/source/javacpp-cpp/$dirName")

    val javacppCompileConfig = tasks.register<JavaCompile>("javacppCompileConfig$variantName") {
        source(javaSourceDirs)
        include("**/javacpp/*.java")
        classpath = javacppConfiguration
        destinationDir = buildDir.resolve("intermediates/javac/$dirName/javacpp-config")
    }

    val javacppGenerateJavaSources = tasks.register<JavaExec>("javacppGenerateJavaSources$variantName") {
        inputs.files(javacppCompileConfig.map { it.source }, fileTree(cppSourceDir) { exclude("generated") })
        outputs.dir(generatedJavaSourceDir)

        dependsOn(javacppCompileConfig)
        main = "org.bytedeco.javacpp.tools.Builder"
        classpath = javacppConfiguration
        args = listOf(
            "-classpath", javacppCompileConfig.get().destinationDir.toString(),
            "-clean",
            "-d", generatedJavaSourceDir.toString(),
            "-nogenerate",
            "-Dplatform.includepath=${file("src/main/cpp")}",
            "org.equeim.libtremotesf.javacpp.LibTremotesfConfig"
        )
    }
    registerJavaGeneratingTask(javacppGenerateJavaSources.get(), generatedJavaSourceDir)

    val javacppCompileGeneratedJavaSources = tasks.register<JavaCompile>("javacppCompileGeneratedJavaSources$variantName") {
        dependsOn(javacppGenerateJavaSources)
        source(generatedJavaSourceDir)
        classpath = javacppConfiguration + files(javacppCompileConfig.get().destinationDir)
        destinationDir = buildDir.resolve("intermediates/javac/$dirName/javacpp-generated")
    }

    val javacppGenerateCppSources = tasks.register<JavaExec>("javacppGenerateCppSources$variantName") {
        inputs.dir(cppSourceDir)
        inputs.dir(generatedJavaSourceDir)
        outputs.dir(generatedCppSourceDir)

        dependsOn(javacppCompileGeneratedJavaSources)
        main = "org.bytedeco.javacpp.tools.Builder"
        classpath = javacppConfiguration
        args = listOf(
            "-classpath", javacppCompileConfig.get().destinationDir.toString(),
            "-classpath", javacppCompileGeneratedJavaSources.get().destinationDir.toString(),
            "-clean",
            "-d", generatedCppSourceDir.toString(),
            "-nocompile",
            "-Dplatform.includepath=${file("src/main/cpp")}",
            "org.equeim.libtremotesf.*"
        )
    }

    prepareNativeSources.configure {
        dependsOn(javacppGenerateCppSources)
    }

    clean.configure {
        delete(generatedCppSourceDir)
    }
}

dependencies {
    implementation(files(QtTask.jar(qtDir)).builtBy(prepareNativeSources))

    //val javacppDependency = "org.bytedeco:javacpp:${Versions.javacpp}"
    val javacppDependency = files("/home/alexey/projects/javacpp/target/javacpp.jar")
    api(javacppDependency)
    "javacpp"(javacppDependency)
}
