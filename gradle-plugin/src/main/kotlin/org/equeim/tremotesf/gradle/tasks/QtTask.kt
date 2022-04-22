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
import java.lang.module.ModuleDescriptor
import java.nio.file.Files
import javax.inject.Inject

@CacheableTask
abstract class QtTask : DefaultTask() {
    @get:Inject
    protected abstract val gradle: Gradle

    @get:Inject
    protected abstract val providerFactory: ProviderFactory

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
        logger.lifecycle("Start building Qt")
        logger.lifecycle("CMake binary dir = {}", cmakeBinaryDir.orNull)
        logger.lifecycle("Additional CMake flags for host Qt = {}", hostQtCmakeFlags.get())
        logger.lifecycle("Ccache = {}", ccache.get())

        printCMakeInfo(cmakeBinaryDir.orNull, logger)

        if (ccache.get()) {
            zeroCcacheStatistics(logger)
        }

        val hostQtInfo = getHostQtInfo()

        for (abi in NativeAbis.abis) {
            buildQt(abi, hostQtInfo)
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
                listOf("qmake6", "-query", "QT_VERSION"),
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
                listOf("qmake6", "-query", "QT_HOST_PREFIX"),
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
                listOf("qmake6", "-query", "QT_HOST_LIBS"),
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

    private fun buildHostQt() {
        logger.lifecycle("Building host Qt")

        val buildDir = hostBuildDir(rootDir.get())

        val configureFlags = listOf(
            "-release",

            "-prefix", hostInstallDir(rootDir.get()).toString(),

            if (ccache.get()) "-ccache" else "-no-ccache",
            // Precompiled headers cause a lot of cache misses even with right CCACHE_SLOPPINESS values
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

        val buildDir = buildDir(rootDir.get(), abi)

        val configureFlags = listOf(
            "-release",

            "-prefix", installDir(rootDir.get(), abi).toString(),

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

        executeCommand(
            listOf(sourceDir.get().resolve("configure").toString()) + configureFlags,
            logger,
            outputMode = ExecOutputMode.RedirectOutputToFile(buildDir.resolve(CONFIGURE_LOG_FILE))
        ) {
            directory(buildDir)
            cmakeBinaryDir.orNull?.let { prependPath(it) }
        }.also {
            logger.lifecycle("Configuration finished, elapsed time = {}", it.elapsedTime.format())
        }

        logger.lifecycle("Building Qt")

        if (needLinkerWorkaround(crossCompiling)) {
            // Workaround for CMake bug that forces use of gold linker when LTCG is enabled
            // https://gitlab.kitware.com/cmake/cmake/-/issues/21772
            // https://github.com/android/ndk/issues/1444
            executeCommand(
                listOf(
                    "sed",
                    "-i",
                    "s/-fuse-ld=gold//g",
                    buildDir.resolve("build.ninja").toString()
                ), logger
            )
        }


        executeCMake(
            CMakeMode.Build,
            cmakeBinaryDir.orNull,
            buildDir,
            logger,
            gradle
        ).also {
            logger.lifecycle("Building finished, elapsed time = {}", it.elapsedTime.format())
        }

        logger.lifecycle("Installing Qt")
        executeCMake(CMakeMode.Install, cmakeBinaryDir.orNull, buildDir, logger, gradle)
            .also {
                logger.lifecycle(
                    "Installation finished, elapsed time = {}",
                    it.elapsedTime.format()
                )
            }
    }

    private fun needLinkerWorkaround(crossCompiling: Boolean): Boolean {
        if (!crossCompiling) return false
        val cmakeVersion = runCatching { ModuleDescriptor.Version.parse(cmakeVersion.get()) }
            .getOrNull() ?: return true
        return cmakeVersion < ModuleDescriptor.Version.parse(CMAKE_VERSION_WITHOUT_LINKER_BUG)
    }

    companion object {
        fun sourceDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("qtbase")
        fun patchesDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("patches")

        private fun hostBuildDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("build-host")
        private fun hostInstallDir(rootDir: File) = rootDir.resolve(QT_DIR).resolve("install-host")

        private fun buildDir(rootDir: File, abi: String) = rootDir.resolve(QT_DIR).resolve("build-$abi")
        private fun buildDirs(rootDir: File) = NativeAbis.abis.map { buildDir(rootDir, it) }

        private fun installDir(rootDir: File, abi: String) = rootDir.resolve(QT_DIR).resolve("install-$abi")
        private fun installDirs(rootDir: File) = NativeAbis.abis.map { installDir(rootDir, it) }

        fun dirsToClean(rootDir: File) =
            listOf(hostBuildDir(rootDir), hostInstallDir(rootDir)) + buildDirs(rootDir) + installDirs(rootDir)

        fun jar(rootDir: File) =
            installDir(rootDir, NativeAbis.abis.first()).resolve("jar/Qt6Android.jar")

        private const val CMAKE_VERSION_WITHOUT_LINKER_BUG = "3.20.0"
    }
}
