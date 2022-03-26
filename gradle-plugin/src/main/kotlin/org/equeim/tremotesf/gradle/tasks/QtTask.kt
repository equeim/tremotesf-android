package org.equeim.tremotesf.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
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
    abstract val minSdkVersion: Property<String>

    @get:Input
    abstract val qtDir: Property<File>

    @get:InputFiles
    abstract val opensslInstallDirs: ListProperty<File>

    @get:Input
    abstract val sdkDir: Property<File>

    @get:Input
    abstract val ndkDir: Property<File>

    @get:InputDirectory
    val sourceDir: Provider<File> by lazy { qtDir.map { sourceDir(it) } }

    @Suppress("unused")
    @get:OutputDirectories
    val installDirs: Provider<List<File>> by lazy {
        qtDir.map { listOf(hostInstallDir(it)) + installDirs(it) }
    }

    @get:Input
    @get:Optional
    abstract val cmakeBinaryDir: Property<File>

    private val cmakeBinary: String
        get() = cmakeBinaryDir.orNull?.resolve(CMAKE)?.toString() ?: CMAKE

    @get:Input
    abstract val hostQtCmakeFlags: ListProperty<String>

    @get:Input
    abstract val ccache: Property<Boolean>

    @TaskAction
    fun buildQt() {
        logger.lifecycle("Start building Qt")
        logger.lifecycle("Make jobs count = {}", makeJobsCount(gradle))
        logger.lifecycle("CMake binary dir = {}", cmakeBinaryDir.orNull)
        logger.lifecycle("Additional CMake flags for host Qt = {}", hostQtCmakeFlags.get())
        logger.lifecycle("Ccache = {}", ccache.get())

        execOperations.printCMakeInfo(cmakeBinary, logger)

        if (ccache.get()) {
            execOperations.zeroCcacheStatistics(logger)
        }

        val hostQtInfo = getHostQtInfo()

        for (abi in NativeAbis.abis) {
            buildQt(abi, hostQtInfo)
        }

        if (ccache.get()) {
            execOperations.showCcacheStatistics(logger)
        }
    }

    private data class HostQtInfo(val prefix: String, val cmakeDir: String)

    private fun getHostQtInfo(): HostQtInfo {
        val prebuiltHostQtInfo = getPrebuiltHostQtInfo()
        return if (prebuiltHostQtInfo != null) {
            logger.lifecycle("Using prebuilt host Qt")
            prebuiltHostQtInfo
        } else {
            buildHostQt()
            val hostInstallDir = hostInstallDir(qtDir.get())
            HostQtInfo(hostInstallDir.toString(), hostInstallDir.resolve("lib/cmake").toString())
        }
    }

    private fun getPrebuiltHostQtInfo(): HostQtInfo? {
        val buildingQtVersion = runCatching {
            execOperations.exec(logger, ExecOutputMode.Capture) {
                commandLine("git", "-C", sourceDir.get(), "describe", "--tags")
            }.trimmedOutputString().drop(1)
        }.getOrElse {
            throw RuntimeException("Failed to determine Qt version", it)
        }

        logger.lifecycle("Building Qt {}", buildingQtVersion)

        val hostQtVersion = runCatching {
            execOperations.exec(logger, ExecOutputMode.Capture) {
                commandLine("qmake6", "-query", "QT_VERSION")
            }.trimmedOutputString()
        }.getOrElse { return null }

        logger.lifecycle("Found prebuilt host Qt {}", hostQtVersion)

        if (hostQtVersion != buildingQtVersion) {
            logger.lifecycle("Prebuilt host Qt version is incompatible")
            return null
        }

        val hostPrefix = runCatching {
            execOperations.exec(logger, ExecOutputMode.Capture) {
                commandLine("qmake6", "-query", "QT_HOST_PREFIX")
            }.trimmedOutputString()
        }.getOrElse {
            logger.error("Failed to get QT_HOST_PREFIX")
            return null
        }

        val hostLibs = runCatching {
            execOperations.exec(logger, ExecOutputMode.Capture) {
                commandLine("qmake6", "-query", "QT_HOST_LIBS")
            }.trimmedOutputString()
        }.getOrElse {
            logger.error("Failed to get QT_HOST_LIBS")
            return null
        }

        return HostQtInfo(hostPrefix, "${hostLibs}/cmake")
    }

    private fun buildHostQt() {
        logger.lifecycle("Building host Qt")

        val buildDir = hostBuildDir(qtDir.get())

        val configureFlags = listOf(
            "-release",

            "-prefix", hostInstallDir(qtDir.get()).toString(),

            if (ccache.get()) "-ccache" else "-no-ccache",
            // Precombiled headers cause a lot of cache misses even with right CCACHE_SLOPPINESS values
            if (ccache.get()) "-no-pch" else "-pch",

            "-nomake", "examples",

            "-no-feature-concurrent",
            "-no-feature-dbus",
            "-no-feature-gui",
            "-no-feature-network",
            "-no-feature-sql",
            "-no-feature-testlib",

            "-no-feature-animation",
            "-no-feature-cborstreamwriter",
            "-no-feature-concatenatetablesproxymodel",
            "-no-feature-datetimeparser",
            "-no-feature-easingcurve",
            "-no-feature-filesystemiterator",
            "-no-feature-filesystemwatcher",
            "-no-feature-gestures",
            "-no-feature-hijricalendar",
            "-no-feature-identityproxymodel",
            "-no-feature-islamiccivilcalendar",
            "-no-feature-jalalicalendar",
            "-no-feature-mimetype",
            "-no-feature-process",
            "-no-feature-processenvironment",
            "-no-feature-proxymodel",
            "-no-feature-relocatable",
            "-no-feature-sharedmemory",
            "-no-feature-shortcut",
            "-no-feature-sortfilterproxymodel",
            "-no-feature-stringlistmodel",
            "-no-feature-systemsemaphore",
            "-no-feature-temporaryfile",
            "-no-feature-translation",
            "-no-feature-transposeproxymodel",
            "--", "-G", "Ninja"
        ) + hostQtCmakeFlags.get()

        buildQt(buildDir, configureFlags, false)
    }

    private fun buildQt(abi: String, hostQtInfo: HostQtInfo) {
        logger.lifecycle("Building Qt for abi = {}", abi)

        val buildDir = buildDir(qtDir.get(), abi)

        val configureFlags = listOf(
            "-release",

            "-prefix", installDir(qtDir.get(), abi).toString(),

            "-platform", "android-clang",
            "-android-sdk", sdkDir.get().toString(),
            "-android-ndk", ndkDir.get().toString(),
            "-android-ndk-platform", "android-${minSdkVersion.get()}",
            "-android-abis", abi,

            if (ccache.get()) "-ccache" else "-no-ccache",
            // Precombiled headers cause a lot of cache misses even with right CCACHE_SLOPPINESS values
            if (ccache.get()) "-no-pch" else "-pch",
            // Ccache can't cache when linking and with LTCG/LTO a lot of time-consuming operations happen there,
            // which makes ccache not very effective. Just disable LTCG
            if (ccache.get()) "-no-ltcg" else "-ltcg",

            "-nomake", "examples",

            "-no-feature-dbus",
            "-no-feature-gui",
            "-no-feature-sql",
            "-no-feature-testlib",
            "-no-feature-xml",

            "-no-feature-animation",
            "-no-feature-cborstreamwriter",
            "-no-feature-commandlineparser",
            "-no-feature-concatenatetablesproxymodel",
            "-no-feature-datetimeparser",
            "-no-feature-dnslookup",
            "-no-feature-dtls",
            "-no-feature-easingcurve",
            "-no-feature-filesystemiterator",
            "-no-feature-filesystemwatcher",
            "-no-feature-gestures",
            "-no-feature-gssapi",
            "-no-feature-hijricalendar",
            "-no-feature-identityproxymodel",
            "-no-feature-islamiccivilcalendar",
            "-no-feature-jalalicalendar",
            "-no-feature-localserver",
            "-no-feature-mimetype",
            "-no-feature-networkdiskcache",
            "-no-feature-process",
            "-no-feature-processenvironment",
            "-no-feature-proxymodel",
            "-no-feature-relocatable",
            "-no-feature-settings",
            "-no-feature-sharedmemory",
            "-no-feature-shortcut",
            "-no-feature-sortfilterproxymodel",
            "-no-feature-sspi",
            "-no-feature-stringlistmodel",
            "-no-feature-systemsemaphore",
            "-no-feature-temporaryfile",
            "-no-feature-topleveldomain",
            "-no-feature-translation",
            "-no-feature-transposeproxymodel",
            "-no-feature-udpsocket",
            "-no-feature-xmlstream",
            "-no-feature-xmlstreamreader",
            "-no-feature-xmlstreamwriter",
            "-openssl-linked",
            "--",
            "-G", "Ninja",
            "-DCMAKE_FIND_ROOT_PATH=${opensslInstallDirs.get()[NativeAbis.abis.indexOf(abi)]}",
            "-DQT_HOST_PATH=${hostQtInfo.prefix}",
            "-DQT_HOST_PATH_CMAKE_DIR=${hostQtInfo.cmakeDir}",
            // Remove when updating Qt to 6.3
            "-DANDROID_PLATFORM=${minSdkVersion.get()}"
        )

        buildQt(buildDir, configureFlags, true)
    }

    private fun buildQt(buildDir: File, configureFlags: List<String>, crossCompiling: Boolean) {
        logger.lifecycle("Configuring Qt")

        Files.createDirectories(buildDir.toPath())

        measureNanoTime {
            execOperations.exec(logger) {
                executable(sourceDir.get().resolve("configure"))
                args = configureFlags
                workingDir = buildDir
                cmakeBinaryDir.orNull?.let { cmakeBinaryDir ->
                    prependPath(cmakeBinaryDir)
                }
            }
        }.also {
            logger.lifecycle(
                "Configuration finished, elapsed time = {} s",
                nanosToSecondsString(it)
            )
        }

        logger.lifecycle("Building Qt")

        if (crossCompiling) {
            // Workaround for CMake bug that forces use of gold linker when LTCG is enabled
            // https://gitlab.kitware.com/cmake/cmake/-/issues/21772
            // https://github.com/android/ndk/issues/1444
            // Should be unnecessary with NDK r23 or newer + CMake 3.20 or newer, but there is no
            // point in trying to check it
            execOperations.exec(logger) {
                commandLine("sed", "-i", "s/-fuse-ld=gold//g", buildDir.resolve("build.ninja"))
            }
        }

        measureNanoTime {
            execOperations.cmake(
                cmakeBinary,
                CMakeMode.Build,
                qtDir.get(),
                buildDir,
                logger,
                gradle
            )
        }.also {
            logger.lifecycle("Building finished, elapsed time = {} s", nanosToSecondsString(it))
        }

        logger.lifecycle("Installing Qt")
        measureNanoTime {
            execOperations.cmake(cmakeBinary, CMakeMode.Install, qtDir.get(), buildDir, logger, gradle)
        }.also {
            logger.lifecycle("Installation finished, elapsed time = {} s", nanosToSecondsString(it))
        }
    }

    companion object {
        fun sourceDir(qtDir: File) = qtDir.resolve("qtbase")
        fun patchesDir(qtDir: File) = qtDir.resolve("patches")

        private fun hostBuildDir(qtDir: File) = qtDir.resolve("build-host")
        private fun hostInstallDir(qtDir: File) = qtDir.resolve("install-host")

        private fun buildDir(qtDir: File, abi: String) = qtDir.resolve("build-$abi")
        private fun buildDirs(qtDir: File) = NativeAbis.abis.map { buildDir(qtDir, it) }

        private fun installDir(qtDir: File, abi: String) = qtDir.resolve("install-$abi")
        private fun installDirs(qtDir: File) = NativeAbis.abis.map { installDir(qtDir, it) }

        fun dirsToClean(qtDir: File) =
            listOf(hostBuildDir(qtDir), hostInstallDir(qtDir)) + buildDirs(qtDir) + installDirs(
                qtDir
            )

        fun jar(qtDir: File) =
            installDir(qtDir, NativeAbis.abis.first()).resolve("jar/Qt6Android.jar")
    }
}
