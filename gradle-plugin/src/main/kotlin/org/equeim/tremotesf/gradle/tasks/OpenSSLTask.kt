package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlin.system.measureNanoTime

abstract class OpenSSLTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val gradle: Gradle
) : DefaultTask() {
    @get:Input
    abstract val opensslDir: Property<File>

    @get:Input
    abstract val ndkDir: Property<File>

    @get:InputDirectory
    val sourceDir: Provider<File> by lazy {
        opensslDir.map { sourceDir(it) }
    }

    @get:OutputDirectories
    val installDirs: Provider<List<File>> by lazy {
        opensslDir.map { installDirs(it) }
    }

    @get:Input
    abstract val ccache: Property<Boolean>

    @TaskAction
    fun buildOpenSSL() {
        logger.lifecycle("Start builiding OpenSSL, make jobs count = {}", makeJobsCount(gradle))
        if (ccache.get()) {
            execOperations.zeroCcacheStatistics(logger)
        }
        for (abi in NativeAbis.abis) {
            buildOpenSSL(abi)
        }
        if (ccache.get()) {
            execOperations.showCcacheStatistics(logger)
        }
    }

    private fun buildOpenSSL(abi: String) {
        logger.lifecycle("Building OpenSSL for abi = {}", abi)

        val buildDir = buildDir(opensslDir.get(), abi)
        Files.createDirectories(buildDir.toPath())

        val cflags = COMMON_CFLAGS.toMutableList()
        if (!ccache.get()) {
            /** Enable LTO only when not using ccache. See [QtTask] for explanation */
            cflags.add("-flto=thin")
        }
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
            add("--prefix=${installDir(opensslDir.get(), abi)}")
            addAll(cflags)
        }

        val binDir = ndkDir.get().resolve("toolchains/llvm/prebuilt/linux-x86_64/bin")

        logger.lifecycle("Configuring OpenSSL")
        measureNanoTime {
            execOperations.exec(logger) {
                executable(sourceDir.get().resolve("Configure"))
                args = configureArgs
                workingDir = buildDir
                environment("ANDROID_NDK", ndkDir.get())

                prependPath(binDir)
                if (ccache.get()) {
                    environment("CC", "ccache ${binDir.resolve("clang")}")
                }
            }
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Building OpenSSL")
        measureNanoTime {
            execOperations.make("build_libs", buildDir, logger, gradle) {
                prependPath(binDir)
            }
        }.also {
            logger.lifecycle("Building finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Installing OpenSSL")
        measureNanoTime {
            execOperations.make("install_dev", buildDir, logger, gradle)
        }.also {
            logger.lifecycle("Installation finished, elapsed time = {} s", nanosToSecondsString(it))
        }
    }

    companion object {
        private val COMMON_ARGUMENTS = listOf("no-shared", "no-ssl3", "no-comp", "no-hw", "no-engine")
        private val COMMON_CFLAGS = listOf(
            "-fvisibility=hidden",
            "-fvisibility-inlines-hidden",
            "-O2"
        )

        fun sourceDir(opensslDir: File) = opensslDir.resolve("openssl")
        fun patchesDir(opensslDir: File) = opensslDir.resolve("patches")

        private fun buildDir(opensslDir: File, abi: String) = opensslDir.resolve("build-$abi")
        private fun buildDirs(opensslDir: File) = NativeAbis.abis.map { abi -> buildDir(opensslDir, abi) }

        private fun installDir(opensslDir: File, abi: String) = opensslDir.resolve("install-$abi")
        private fun installDirs(opensslDir: File) = NativeAbis.abis.map { abi -> installDir(opensslDir, abi) }

        fun dirsToClean(opensslDir: File) = buildDirs(opensslDir) + installDirs(opensslDir)
    }
}
