package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions
import org.equeim.tremotesf.gradle.tasks.ExecUtils.MAKE
import org.equeim.tremotesf.gradle.tasks.ExecUtils.exec
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlin.system.measureNanoTime

abstract class OpenSSLTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:Input
    abstract val opensslDir: Property<File>

    @get:Input
    abstract val ndkDir: Property<File>

    @get:InputDirectory
    val sourceDir: Provider<File> by lazy {
        opensslDir.map { sourceDir(it) }
    }

    @get:OutputDirectory
    val installDir: Provider<File> by lazy {
        opensslDir.map { installDir(it) }
    }

    @TaskAction
    fun buildOpenSSL() {
        for (abi in NativeAbis.abis) {
            buildOpenSSL(abi)
        }
    }

    private fun buildOpenSSL(abi: String) {
        logger.lifecycle("Building OpenSSL for abi = {}", abi)

        val buildDir = buildDir(opensslDir.get(), abi)
        Files.createDirectories(buildDir.toPath())

        val cflags = COMMON_CFLAGS.toMutableList()
        cflags.add("-D__ANDROID_API__=${Versions.minSdk}")
        val target = when (abi) {
            "armeabi-v7a" -> {
                cflags.add("-mfpu=neon")
                "android-arm"
            }
            "x86" -> "android-x86"
            "arm64-v8a" -> "android-arm64"
            "x86_64" -> "android-x86_64"
            else -> throw IllegalStateException("Unknown ABI")
        }
        val configureArgs = mutableListOf<String>().apply {
            add(target)
            addAll(COMMON_ARGUMENTS)
            add("--prefix=${installDir.get()}")
            addAll(cflags)
        }
        logger.lifecycle("Configuring OpenSSL")
        measureNanoTime {
            exec(execOperations, sourceDir.get().resolve("Configure").toString(), configureArgs, buildDir, mapOf("ANDROID_NDK" to ndkDir.get()))
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Building OpenSSL")
        measureNanoTime {
            exec(execOperations, MAKE, listOf("build_libs", "-j16"), buildDir)
        }.also {
            logger.lifecycle("Building finished, elapsed time = {} s", nanosToSecondsString(it))
        }
        logger.lifecycle("Installing OpenSSL")
        measureNanoTime {
            exec(execOperations, MAKE, listOf("install_dev"), buildDir)
        }.also {
            logger.lifecycle("Installation finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Renaming static libraries to add ABI suffix")
        val libDir = installDir.get().resolve("lib")
        if (!libDir.resolve("libcrypto.a").renameTo(libDir.resolve("libcrypto_$abi.a"))) {
            throw RuntimeException("Failed to remove libcrypto.a")
        }
        if (!libDir.resolve("libssl.a").renameTo(libDir.resolve("libssl_$abi.a"))) {
            throw RuntimeException("Failed to remove libssl.a")
        }
    }

    companion object {
        private val COMMON_ARGUMENTS = listOf("no-shared", "no-ssl3", "no-comp", "no-hw", "no-engine")
        private val COMMON_CFLAGS = listOf(
            "-fvisibility=hidden",
            "-fvisibility-inlines-hidden",
            "-O2",
            "-flto=thin"
        )

        fun sourceDir(opensslDir: File) = opensslDir.resolve("openssl")
        fun patchesDir(opensslDir: File) = opensslDir.resolve("patches")
        fun buildDir(opensslDir: File, abi: String) = opensslDir.resolve("build-$abi")
        fun buildDirs(opensslDir: File) = NativeAbis.abis.map { abi -> buildDir(opensslDir, abi) }
        fun installDir(opensslDir: File) = opensslDir.resolve("install")
    }
}
