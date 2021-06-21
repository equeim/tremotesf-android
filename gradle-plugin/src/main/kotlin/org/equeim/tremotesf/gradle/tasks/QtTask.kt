package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.Versions
import org.equeim.tremotesf.gradle.tasks.ExecUtils.MAKE
import org.equeim.tremotesf.gradle.tasks.ExecUtils.defaultMakeArguments
import org.equeim.tremotesf.gradle.tasks.ExecUtils.exec
import org.equeim.tremotesf.gradle.tasks.ExecUtils.isNdkEnvironmentVariable
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
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

    @TaskAction
    fun buildQt() {
        logger.lifecycle("Configuring Qt")

        val buildDir = buildDir(qtDir.get())
        Files.createDirectories(buildDir.toPath())

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

        val firstAbi = NativeAbis.abis.first()
        measureNanoTime {
            exec(
                execOperations,
                sourceDir.get().resolve("configure").toString(),
                configureFlags,
                buildDir,
                mapOf("OPENSSL_LIBS" to "-lssl_$firstAbi -lcrypto_$firstAbi")
            ) { isNdkEnvironmentVariable(it) }
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Building Qt")

        measureNanoTime {
            exec(execOperations, MAKE, defaultMakeArguments(gradle), buildDir) { isNdkEnvironmentVariable(it) }
        }.also {
            logger.lifecycle("Building finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Installing Qt")
        measureNanoTime {
            exec(execOperations, MAKE, listOf("install"), buildDir)
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
