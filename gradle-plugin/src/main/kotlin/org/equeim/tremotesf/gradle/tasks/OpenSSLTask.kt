package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.utils.*
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.NormalizeLineEndings
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

@CacheableTask
abstract class OpenSSLTask : DefaultTask() {
    @get:Inject
    protected abstract val gradle: Gradle

    @get:Inject
    protected abstract val providerFactory: ProviderFactory

    private val printBuildLogOnError: Provider<Boolean> by lazy {
        providerFactory.gradleProperty(PRINT_BUILD_LOG_ON_ERROR_PROPERTY).map(String::toBoolean)
    }

    /**
     * Input properties
     */
    @get:Input
    abstract val rootDir: Property<File>

    @get:Input
    abstract val minSdkVersion: Property<String>

    @get:Input
    abstract val ndkDir: Property<File>

    // Added for build cache
    @get:Input
    abstract val ndkVersion: Property<String>

    /**
     * Other inputs
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    protected val sourceDir: Provider<File> by lazy { rootDir.map(::sourceDir) }

    @get:Input
    protected val ccache: Provider<Boolean> by lazy {
        providerFactory.gradleProperty(CCACHE_PROPERTY).map(String::toBoolean)
    }

    /**
     * Outputs
     */
    @get:OutputDirectories
    val installDirs: Provider<List<File>> by lazy { rootDir.map(::installDirs) }

    @TaskAction
    fun buildOpenSSL() {
        logger.lifecycle("Start building OpenSSL")
        if (ccache.get()) {
            zeroCcacheStatistics(logger)
        }
        for (abi in NativeAbis.abis) {
            buildOpenSSL(abi)
        }
        if (ccache.get()) {
            showCcacheStatistics(logger)
        }
        didWork = true
    }

    private fun buildOpenSSL(abi: String) {
        logger.lifecycle("Building OpenSSL for abi = {}", abi)

        val buildDir = buildDir(rootDir.get(), abi)
        Files.createDirectories(buildDir.toPath())

        val cflags = COMMON_CFLAGS.toMutableList()
        if (!ccache.get()) {
            /** Enable LTO only when not using ccache. See [QtTask] for explanation */
            cflags.add("-flto=thin")
        }
        cflags.add("-D__ANDROID_API__=${minSdkVersion.get()}")

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
            add("--prefix=${installDir(rootDir.get(), abi)}")
            addAll(cflags)
        }

        val binDir = ndkDir.get().resolve("toolchains/llvm/prebuilt/linux-x86_64/bin")

        logger.lifecycle("Configuring OpenSSL")
        executeCommand(
            listOf(sourceDir.get().resolve("Configure").toString()) + configureArgs,
            logger,
            outputMode = ExecOutputMode.RedirectOutputToFile(buildDir.resolve(CONFIGURE_LOG_FILE), printBuildLogOnError.get())
        ) {
            directory(buildDir)
            prependPath(binDir)
            environment()["ANDROID_NDK"] = ndkDir.get().toString()
            if (ccache.get()) {
                environment()["CC"] = "ccache ${binDir.resolve("clang")}"
            }
        }.also {
            logger.lifecycle(
                "Configuration finished, elapsed time = {}",
                it.elapsedTime.format()
            )
        }

        logger.lifecycle("Building OpenSSL")
        executeMake("build_libs", buildDir, buildDir.resolve(BUILD_LOG_FILE), printBuildLogOnError.get(), logger, gradle) {
            prependPath(binDir)
        }.also {
            logger.lifecycle("Building finished, elapsed time = {}", it.elapsedTime.format())
        }

        logger.lifecycle("Installing OpenSSL")
        executeMake("install_dev", buildDir, buildDir.resolve(INSTALL_LOG_FILE), printBuildLogOnError.get(), logger, gradle)
            .also {
                logger.lifecycle("Installation finished, elapsed time = {}", it.elapsedTime.format())
            }
    }

    companion object {
        private val COMMON_ARGUMENTS =
            listOf("no-shared", "no-ssl3", "no-comp", "no-hw", "no-engine")
        private val COMMON_CFLAGS = listOf(
            "-fvisibility=hidden",
            "-fvisibility-inlines-hidden",
            "-O2"
        )

        fun sourceDir(rootDir: File) = rootDir.resolve(OPENSSL_DIR).resolve("openssl")
        fun patchesDir(rootDir: File) = rootDir.resolve(OPENSSL_DIR).resolve("patches")

        private fun buildDir(rootDir: File, abi: String) = rootDir.resolve(OPENSSL_DIR).resolve("build-$abi")
        private fun buildDirs(rootDir: File) =
            NativeAbis.abis.map { abi -> buildDir(rootDir, abi) }

        private fun installDir(rootDir: File, abi: String) = rootDir.resolve(OPENSSL_DIR).resolve("install-$abi")
        private fun installDirs(rootDir: File) =
            NativeAbis.abis.map { abi -> installDir(rootDir, abi) }

        fun dirsToClean(rootDir: File) = buildDirs(rootDir) + installDirs(rootDir)
    }
}
