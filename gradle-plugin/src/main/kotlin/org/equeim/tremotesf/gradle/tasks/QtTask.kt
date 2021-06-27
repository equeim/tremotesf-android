package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.environment
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import kotlin.system.measureNanoTime

abstract class QtTask @Inject constructor(
    private val execOperations: ExecOperations,
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
    val sourceDir: Provider<File> by lazy { qtDir.map { sourceDir(it) } }

    @get:OutputDirectory
    val installDir: Provider<File> by lazy { qtDir.map { installDir(it) } }

    @get:Input
    abstract val ccache: Property<Boolean>

    @TaskAction
    fun buildQt() {
        logger.lifecycle("Start building Qt, make jobs count = {}", makeJobsCount(gradle))

        logger.lifecycle("Configuring Qt")

        val buildDir = buildDir(qtDir.get())
        Files.createDirectories(buildDir.toPath())

        if (ccache.get()) {
            execOperations.zeroCcacheStatistics(logger)
        }

        val configureFlags = listOf(
            "-v",
            "-confirm-license",
            "-opensource",
            "-xplatform", "android-clang",
            "-c++std", "c++1z",
            "-android-ndk", ndkDir.get().toString(),
            "-android-sdk", sdkDir.get().toString(),
            "-android-ndk-host", "linux-x86_64",
            "-android-abis", NativeAbis.abis.joinToString(separator = ","),
            "-android-ndk-platform", "android-${Versions.minSdk}",
            "-prefix", installDir.get().toString(),
            "-linker", "lld",
            if (ccache.get()) "-ccache" else "-no-ccache",
            // Precombiled headers cause a lot of cache misses even with right CCACHE_SLOPPINESS values
            if (ccache.get()) "-no-pch" else "-pch",
            // Ccache can't cache when linking and with LTCG/LTO a lot of time-consuming operations happen there,
            // which makes ccache not very effective. Just disable LTCG
            if (ccache.get()) "-no-ltcg" else "-ltcg",

            "-nomake", "examples",
            "-nomake", "tests",

            "-no-feature-dbus",
            "-no-feature-gui",
            "-no-feature-sql",
            "-no-feature-testlib",
            "-no-feature-xml",

            "-no-feature-animation",
            "-no-feature-bearermanagement",
            "-no-feature-big_codecs",
            "-no-feature-codecs",
            "-no-feature-commandlineparser",
            "-no-feature-concatenatetablesproxymodel",
            "-no-feature-cursor",
            "-no-feature-datetimeparser",
            "-no-feature-dnslookup",
            "-no-feature-dtls",
            "-no-feature-easingcurve",
            "-no-feature-filesystemiterator",
            "-no-feature-filesystemwatcher",
            "-no-feature-ftp",
            "-no-feature-gestures",
            "-no-feature-gssapi",
            "-no-feature-hijricalendar",
            "-no-feature-iconv",
            "-no-feature-identityproxymodel",
            "-no-feature-islamiccivilcalendar",
            "-no-feature-itemmodel",
            "-no-feature-jalalicalendar",
            "-no-feature-localserver",
            "-no-feature-mimetype",
            "-no-feature-networkdiskcache",
            "-no-feature-process",
            "-no-feature-processenvironment",
            "-no-feature-proxymodel",
            "-no-feature-regularexpression",
            "-no-feature-settings",
            "-no-feature-sharedmemory",
            "-no-feature-shortcut",
            "-no-feature-sortfilterproxymodel",
            "-no-feature-sspi",
            "-no-feature-statemachine",
            "-no-feature-stringlistmodel",
            "-no-feature-systemsemaphore",
            "-no-feature-temporaryfile",
            "-no-feature-textcodec",
            "-no-feature-translation",
            "-no-feature-transposeproxymodel",
            "-no-feature-udpsocket",
            "-no-feature-xmlstream",
            "-no-feature-xmlstreamreader",
            "-no-feature-xmlstreamwriter",
            "-openssl-linked",
            "-I${opensslInstallDir.get().resolve("include")}",
            "-L${opensslInstallDir.get().resolve("lib")}"
        )

        val firstAbi = NativeAbis.abis.first()
        measureNanoTime {
            execOperations.exec(logger) {
                executable(sourceDir.get().resolve("configure"))
                args = configureFlags
                workingDir = buildDir
                environment(
                    "OPENSSL_LIBS" to "-lssl_$firstAbi -lcrypto_$firstAbi",
                    "MAKEOPTS" to defaultMakeArguments(gradle).joinToString(" ")
                )
            }
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Building Qt")

        measureNanoTime {
            execOperations.make(buildDir, logger, gradle)
        }.also {
            logger.lifecycle("Building finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        if (ccache.get()) {
            execOperations.showCcacheStatistics(logger)
        }

        logger.lifecycle("Installing Qt")
        measureNanoTime {
            execOperations.make("install", buildDir, logger, gradle)
        }.also {
            logger.lifecycle("Installation finished, elapsed time = {} s", nanosToSecondsString(it))
        }
    }

    companion object {
        fun sourceDir(qtDir: File) = qtDir.resolve("qtbase")
        fun patchesDir(qtDir: File) = qtDir.resolve("patches")
        fun buildDir(qtDir: File) = qtDir.resolve("build")
        fun installDir(qtDir: File) = qtDir.resolve("install")
        fun jar(qtDir: File) = installDir(qtDir).resolve("jar/QtAndroid.jar")
    }
}
