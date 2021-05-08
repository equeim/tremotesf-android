package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.tasks.ExecUtils.MAKE
import org.equeim.tremotesf.gradle.tasks.ExecUtils.exec
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

abstract class OpenSSLTask @Inject constructor(
    private val execActionFactory: ExecActionFactory
) : DefaultTask() {
    @get:Input
    abstract val opensslDir: Property<File>

    @get:Input
    abstract val ndkDir: Property<File>

    @get:InputDirectory
    val sourceDir: Provider<File> by lazy {
        opensslDir.map { it.resolve(SOURCE_DIR) }
    }

    @get:OutputDirectory
    val installDir: Provider<File> by lazy {
        opensslDir.map { it.resolve(INSTALL_DIR) }
    }

    @TaskAction
    fun buildOpenSSL() {
        for ((api, abis) in NativeAbis.apisToAbis) {
            for (abi in abis) {
                buildOpenSSL(api, abi)
            }
        }
    }

    private fun buildOpenSSL(api: Int, abi: String) {
        logger.info("Building OpenSSL for api = $api, abi = $abi")

        val buildDir = buildDir(opensslDir.get(), abi)
        Files.createDirectories(buildDir.toPath())

        val cflags = COMMON_CFLAGS.toMutableList()
        cflags.add("-D__ANDROID_API__=$api")
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
        logger.info("Configuring OpenSSL")
        exec(execActionFactory, sourceDir.get().resolve("Configure").toString(), configureArgs, buildDir, mapOf("ANDROID_NDK" to ndkDir.get()))

        logger.info("Building OpenSSL")
        exec(execActionFactory, MAKE, listOf("build_libs", "-j16"), buildDir)
        logger.info("Installing OpenSSL")
        exec(execActionFactory, MAKE, listOf("install_dev"), buildDir)

        logger.info("Renaming static libraries to add ABI suffix")
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

        const val SOURCE_DIR = "openssl"
        const val PATCHES_DIR = "patches"

        fun buildDir(opensslDir: File, abi: String) = opensslDir.resolve("build-$abi")
        fun buildDirs(opensslDir: File) = NativeAbis.apisToAbis.values.flatMap { abis -> abis.map { abi -> buildDir(opensslDir, abi) } }

        const val INSTALL_DIR = "install"
    }
}
