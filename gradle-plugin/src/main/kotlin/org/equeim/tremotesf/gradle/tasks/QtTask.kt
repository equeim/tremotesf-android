// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.utils.*
import org.gradle.api.DefaultTask
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

@CacheableTask
abstract class QtTask : DefaultTask() {
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
    abstract val cmakeVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val cmakeBinaryDir: Property<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val opensslInstallDirs: ListProperty<File>

    @get:Input
    abstract val sdkDir: Property<File>

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
    protected val hostQtCmakeFlags: Provider<List<String>> by lazy {
        providerFactory.gradleProperty("org.equeim.tremotesf.host-qt-cmake-flags")
            .map { it.split(" ").filter(String::isNotBlank) }
    }

    @get:Input
    protected val ccache: Provider<Boolean> by lazy {
        providerFactory.gradleProperty(CCACHE_PROPERTY).map(String::toBoolean)
    }

    /**
     * Outputs
     */
    @get:OutputDirectories
    protected val installDirs: Provider<List<File>> by lazy {
        rootDir.map { installDirs(it) + hostInstallDir(it) }
    }

    @TaskAction
    fun buildQt() {
        logger.lifecycle("===> Start building Qt")
        logger.lifecycle("CMake binary dir = {}", cmakeBinaryDir.orNull)
        logger.lifecycle("Additional CMake flags for host Qt = {}", hostQtCmakeFlags.get())
        logger.lifecycle("Ccache = {}", ccache.get())

        printCMakeInfo(cmakeBinaryDir.orNull, logger)

        if (ccache.get()) {
            zeroCcacheStatistics(logger)
        }

        val hostQtInfo = getHostQtInfo()

        for (abi in NativeAbis.abis) {
            buildAndroidQt(abi, hostQtInfo)
        }

        if (ccache.get()) {
            showCcacheStatistics(logger)
        }

        didWork = true
    }

    private data class HostQtInfo(val prefix: String, val cmakeDir: String)

    private fun getHostQtInfo(): HostQtInfo {
        val prebuiltHostQtInfo = getPrebuiltHostQtInfo()
        return if (prebuiltHostQtInfo != null) {
            logger.lifecycle("Using prebuilt host Qt")
            prebuiltHostQtInfo
        } else {
            buildHostQt()
            val hostInstallDir = hostInstallDir(rootDir.get())
            HostQtInfo(hostInstallDir.toString(), hostInstallDir.resolve("lib/cmake").toString())
        }
    }

    private fun getPrebuiltHostQtInfo(): HostQtInfo? {
        val buildingQtVersion = runCatching {
            executeCommand(
                listOf("git", "-C", sourceDir.get().toString(), "describe", "--tags"),
                logger,
                outputMode = ExecOutputMode.CaptureOutput
            )
                .trimmedOutputString()
                .drop(1)
        }.getOrElse {
            throw RuntimeException("Failed to determine Qt version", it)
        }

        logger.lifecycle("Building Qt {}", buildingQtVersion)

        val hostQtVersion = runCatching {
            executeCommand(
                listOf("qtpaths6", "--query", "QT_VERSION"),
                logger,
                outputMode = ExecOutputMode.CaptureOutput
            )
                .trimmedOutputString()
        }.getOrElse { return null }

        logger.lifecycle("Found prebuilt host Qt {}", hostQtVersion)

        if (hostQtVersion != buildingQtVersion) {
            logger.lifecycle("Prebuilt host Qt version is incompatible")
            return null
        }

        val hostPrefix = runCatching {
            executeCommand(
                listOf("qtpaths6", "--query", "QT_HOST_PREFIX"),
                logger,
                outputMode = ExecOutputMode.CaptureOutput
            )
                .trimmedOutputString()
        }.getOrElse {
            logger.error("Failed to get QT_HOST_PREFIX")
            return null
        }

        val hostLibs = runCatching {
            executeCommand(
                listOf("qtpaths6", "--query", "QT_HOST_LIBS"),
                logger,
                outputMode = ExecOutputMode.CaptureOutput
            )
                .trimmedOutputString()
        }.getOrElse {
            logger.error("Failed to get QT_HOST_LIBS")
            return null
        }

        return HostQtInfo(hostPrefix, "${hostLibs}/cmake")
    }

    private fun commonConfigureFlags(): List<String> = listOf(
        "-release",
        if (ccache.get()) "-ccache" else "-no-ccache",
        // Precompiled headers cause a lot of cache misses even with right CCACHE_SLOPPINESS values
        if (ccache.get()) "-no-pch" else "-pch",
        "-nomake", "examples"
    )

    private val commonDisabledFeatures = listOf(
        "dbus",
        "gui",
        "sql",
        "testlib",
        "xml",

        "androiddeployqt",
        "animation",
        "backtrace",
        // Broken
        //"cborstreamreader",
        "cborstreamwriter",
        "concatenatetablesproxymodel",
        // Broken
        //"datestring",
        "datetimeparser",
        "easingcurve",
        "filesystemiterator",
        "filesystemwatcher",
        "gestures",
        "glib",
        "hijricalendar",
        "icu",
        "identityproxymodel",
        "inotify",
        "islamiccivilcalendar",
        "itemmodel",
        "jalalicalendar",
        "journald",
        "library",
        "macdeployqt",
        "mimetype-database",
        "mimetype",
        "processenvironment",
        "process",
        "proxymodel",
        "qmake",
        // Broken
        //"regularexpression",
        "settings",
        "sharedmemory",
        "shortcut",
        "slog2",
        "sortfilterproxymodel",
        "stringlistmodel",
        "syslog",
        "systemsemaphore",
        "temporaryfile",
        // Broken
        //"textdate",
        "timezone",
        "translation",
        "transposeproxymodel",
        "windeployqt"
    )

    @Suppress("OPT_IN_IS_NOT_ENABLED")
    @OptIn(ExperimentalStdlibApi::class)
    private val hostDisabledFeatures = buildList {
        addAll(commonDisabledFeatures)
        addAll(
            listOf(
                "concurrent",
                "network"
            )
        )
    }

    @Suppress("OPT_IN_IS_NOT_ENABLED")
    @OptIn(ExperimentalStdlibApi::class)
    private val androidDisabledFeatures = buildList {
        addAll(commonDisabledFeatures)
        addAll(
            listOf(
                "commandlineparser",
                "xmlstream",

                "dnslookup",
                "dtls",
                "gssapi",
                "libproxy",
                "localserver",
                "networkdiskcache",
                "networklistmanager",
                "publicsuffix-system",
                "sctp",
                "sspi",
                "system-proxies",
                "topleveldomain",
                "udpsocket",
            )
        )
    }

    private fun List<String>.toDisabledFeatures(): List<String> = map { "-no-feature-$it" }

    private val commonCMakeOptions = listOf(
        "--log-level=STATUS",
        "-G", "Ninja",
    )

    private fun buildHostQt() {
        logger.lifecycle("===> Building host Qt")

        val buildDir = hostBuildDir(rootDir.get())

        @Suppress("OPT_IN_IS_NOT_ENABLED")
        @OptIn(ExperimentalStdlibApi::class)
        val configureFlags = buildList {
            addAll(commonConfigureFlags())
            add("-prefix")
            add(hostInstallDir(rootDir.get()).toString())
            addAll(hostDisabledFeatures.toDisabledFeatures())
            add("--")
            addAll(commonCMakeOptions)
            addAll(hostQtCmakeFlags.get())
        }

        buildQt(buildDir, configureFlags)
    }

    private fun buildAndroidQt(abi: String, hostQtInfo: HostQtInfo) {
        logger.lifecycle("===> Building Qt for abi = {}", abi)

        val buildDir = buildDir(rootDir.get(), abi)

        @Suppress("OPT_IN_IS_NOT_ENABLED")
        @OptIn(ExperimentalStdlibApi::class)
        val configureFlags = buildList {
            addAll(commonConfigureFlags())
            addAll(
                listOf(
                    "-prefix", installDir(rootDir.get(), abi).toString(),
                    "-platform", "android-clang",
                    "-android-sdk", sdkDir.get().toString(),
                    "-android-ndk", ndkDir.get().toString(),
                    "-android-ndk-platform", "android-${minSdkVersion.get()}",
                    "-android-abis", abi,
                    // Ccache can't cache when linking and with LTCG/LTO a lot of time-consuming operations happen there,
                    // which makes ccache not very effective. Just disable LTCG
                    if (ccache.get()) "-no-ltcg" else "-ltcg",
                    "-openssl-linked"
                )
            )
            addAll(androidDisabledFeatures.toDisabledFeatures())
            add("--")
            addAll(commonCMakeOptions)
            addAll(
                listOf(
                    "-DCMAKE_FIND_ROOT_PATH=${opensslInstallDirs.get()[NativeAbis.abis.indexOf(abi)]}",
                    "-DQT_HOST_PATH=${hostQtInfo.prefix}",
                    "-DQT_HOST_PATH_CMAKE_DIR=${hostQtInfo.cmakeDir}",
                    // Fix CMake forcing gold linker
                    "-DCMAKE_ANDROID_NDK_VERSION=${ndkVersion.get().splitToSequence('.').first()}"
                )
            )
        }

        buildQt(buildDir, configureFlags)
    }

    private fun buildQt(buildDir: File, configureFlags: List<String>) {
        logger.lifecycle("Configuring Qt")

        Files.createDirectories(buildDir.toPath())

        executeCommand(
            listOf(sourceDir.get().resolve("configure").toString()) + configureFlags,
            logger,
            outputMode = ExecOutputMode.RedirectOutputToFile(
                buildDir.resolve(CONFIGURE_LOG_FILE),
                printBuildLogOnError.get()
            )
        ) {
            directory(buildDir)
            cmakeBinaryDir.orNull?.let { prependPath(it) }
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {}", it.elapsedTime.format())
        }

        logger.lifecycle("Building Qt")

        executeCMake(
            CMakeMode.Build,
            printBuildLogOnError.get(),
            cmakeBinaryDir.orNull,
            buildDir,
            logger,
            gradle
        ).also {
            logger.lifecycle("Building finished, elapsed time = {}", it.elapsedTime.format())
        }

        logger.lifecycle("Installing Qt")
        executeCMake(
            CMakeMode.Install,
            printBuildLogOnError.get(),
            cmakeBinaryDir.orNull,
            buildDir,
            logger,
            gradle
        )
            .also {
                logger.lifecycle(
                    "Installation finished, elapsed time = {}",
                    it.elapsedTime.format()
                )
            }
    }

    companion object {
        fun sourceDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("qtbase")
        fun patchesDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("patches")

        private fun hostBuildDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("build-host")
        private fun hostInstallDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("install-host")

        private fun buildDir(rootDir: File, abi: String) =
            rootDir.resolve(QT_DIR).resolve("build-$abi")

        private fun buildDirs(rootDir: File) = NativeAbis.abis.map { buildDir(rootDir, it) }

        private fun installDir(rootDir: File, abi: String) =
            rootDir.resolve(QT_DIR).resolve("install-$abi")

        private fun installDirs(rootDir: File) = NativeAbis.abis.map { installDir(rootDir, it) }

        fun dirsToClean(rootDir: File) =
            listOf(
                hostBuildDir(rootDir),
                hostInstallDir(rootDir)
            ) + buildDirs(rootDir) + installDirs(rootDir)

        fun jar(rootDir: File) =
            installDir(rootDir, NativeAbis.abis.first()).resolve("jar/Qt6Android.jar")
    }
}
