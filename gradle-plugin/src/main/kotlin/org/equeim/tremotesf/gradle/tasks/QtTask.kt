package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions
import org.equeim.tremotesf.gradle.tasks.ExecUtils.MAKE
import org.equeim.tremotesf.gradle.tasks.ExecUtils.defaultMakeArguments
import org.equeim.tremotesf.gradle.tasks.ExecUtils.exec
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.ExecActionFactory
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

abstract class QtTask @Inject constructor(
    private val execActionFactory: ExecActionFactory,
    private val gradle: Gradle
) : DefaultTask() {
    @get:Input
    abstract val qtDir: Property<File>

    @get:InputDirectory
    abstract val opensslInstallDir: Property<File>

    @get:Input
    abstract val sdkDir: Property<File>

    @get:Input
    abstract val ndkDir: Property<File>

    @get:InputDirectory
    val sourceDir: Provider<File> by lazy {
        qtDir.map { it.resolve(SOURCE_DIR) }
    }

    @get:OutputDirectories
    val installDirs: Provider<Iterable<File>> by lazy { qtDir.map { installPrefixes(it) } }

    @TaskAction
    fun buildQt() {
        val commonConfigureFlags = listOf(
            "-v",
            "-confirm-license",
            "-opensource",
            "-xplatform", "android-clang",
            "-c++std", "c++1z",
            "-android-ndk", ndkDir.get().toString(),
            "-android-sdk", sdkDir.get().toString(),
            "-android-ndk-host", "linux-x86_64",
            "-linker", "lld",
            "-ltcg",
            "-no-use-gold-linker",
            "-nomake", "examples",
            "-nomake", "tests",
            "-no-dbus",
            "-no-gui",
            "-no-feature-animation",
            "-no-feature-bearermanagement",
            "-no-feature-big_codecs",
            "-no-feature-codecs",
            "-no-feature-commandlineparser",
            "-no-feature-datetimeparser",
            "-no-feature-dnslookup",
            "-no-feature-dom",
            "-no-feature-dtls",
            "-no-feature-filesystemiterator",
            "-no-feature-filesystemwatcher",
            "-no-feature-ftp",
            "-no-feature-itemmodel",
            "-no-feature-localserver",
            "-no-feature-mimetype",
            "-no-feature-networkdiskcache",
            "-no-feature-process",
            "-no-feature-processenvironment",
            "-no-feature-settings",
            "-no-feature-sql",
            "-no-feature-sharedmemory",
            "-no-feature-statemachine",
            "-no-feature-systemsemaphore",
            "-no-feature-temporaryfile",
            "-no-feature-testlib",
            "-no-feature-textcodec",
            "-no-feature-translation",
            "-no-feature-udpsocket",
            "-no-feature-xml",
            "-no-feature-xmlstream",
            "-no-feature-regularexpression",
            "-openssl-linked",
            "-I${opensslInstallDir.get().resolve("include")}",
            "-L${opensslInstallDir.get().resolve("lib")}"
        )

        NativeAbis.apisToAbis.forEach { (api, abis) -> buildQt(api, abis, commonConfigureFlags) }
    }

    private fun buildQt(api: Int, abis: List<String>, commonConfigureFlags: List<String>) {
        logger.info("Building Qt for api = $api, abis = $abis")

        val buildDir = buildDir(qtDir.get(), api)
        Files.createDirectories(buildDir.toPath())

        val configureFlags = commonConfigureFlags + listOf(
            "-prefix", installPrefix(qtDir.get(), api).toString(),
            "-android-abis", abis.joinToString(separator = ","),
            "-android-ndk-platform", "android-$api"
        )
        val firstAbi = abis.first()
        logger.info("Configuring Qt")
        exec(
            execActionFactory,
            sourceDir.get().resolve("configure").toString(),
            configureFlags,
            buildDir,
            mapOf("OPENSSL_LIBS" to "-lssl_$firstAbi -lcrypto_$firstAbi")
        )

        logger.info("Building Qt")
        exec(execActionFactory, MAKE, defaultMakeArguments(gradle), buildDir)
        logger.info("Installing Qt")
        exec(execActionFactory, MAKE, listOf("install"), buildDir)
    }

    companion object {
        const val SOURCE_DIR = "qtbase"
        const val PATCHES_DIR = "patches"

        fun buildDir(qtDir: File, api: Int) = qtDir.resolve("build-api$api")
        fun buildDirs(qtDir: File) = NativeAbis.apisToAbis.keys.map { buildDir(qtDir, it) }

        fun installPrefix(qtDir: File, api: Int) = qtDir.resolve("install-api$api")
        fun installPrefixes(qtDir: File) = NativeAbis.apisToAbis.keys.map { installPrefix(qtDir, it) }

        fun jar(qtDir: File) = installPrefix(qtDir, Versions.minSdk).resolve("jar/QtAndroid.jar")
    }
}
