// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.utils.ANDROID_TRIPLETS
import org.equeim.tremotesf.gradle.utils.HOST_TRIPLET
import org.equeim.tremotesf.gradle.utils.overlayTripletsDir
import org.equeim.tremotesf.gradle.utils.vcpkgRoot
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

@CacheableTask
abstract class GenerateOverlayTripletsTask : DefaultTask() {
    @get:Inject
    protected abstract val projectLayout: ProjectLayout

    @get:Inject
    protected abstract val providerFactory: ProviderFactory

    @get:Input
    abstract val minSdkVersion: Property<String>

    @get:Input
    abstract val ndkVersionMajor: Property<String>

    private val vcpkgRoot by lazy { vcpkgRoot(projectLayout) }

    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY)
    protected val androidTriplets: Provider<List<Path>> by lazy {
        val triplets = ANDROID_TRIPLETS.map { triplet ->
            vcpkgRoot.resolve("triplets/community/${triplet}.cmake")
        }
        providerFactory.provider { triplets }
    }

    @get:InputFile @get:PathSensitive(PathSensitivity.NAME_ONLY)
    protected val hostTriplet: Provider<Path> by lazy {
        val triplet = vcpkgRoot.resolve("triplets/${HOST_TRIPLET}.cmake")
        providerFactory.provider { triplet }
    }

    @get:OutputDirectory
    protected val overlayTripletsDir: Provider<Path> by lazy { overlayTripletsDir(projectLayout) }

    @TaskAction
    fun generateOverlayTriplets() {
        logger.lifecycle("Generating overlay triplets")

        val overlayTripletsDir = this.overlayTripletsDir.get()
        Files.createDirectories(overlayTripletsDir)

        val appendToAndroid = """

            # MODIFIED

            set(VCPKG_BUILD_TYPE release)
            set(VCPKG_CRT_LINKAGE dynamic)
            # vcpkg's android toolchain file uses it to set ANDROID_NATIVE_API_LEVEL
            set(VCPKG_CMAKE_SYSTEM_VERSION ${minSdkVersion.get()})
            set(
                VCPKG_CMAKE_CONFIGURE_OPTIONS
                # Fix CMake forcing gold linker
                -DCMAKE_ANDROID_NDK_VERSION=${ndkVersionMajor.get()}
                # These are needed for Qt port. Qt's CMake files intercept these values before vcpkg's toolchain file sets them,
                # so set them manually in advance
                -DANDROID_PLATFORM=${minSdkVersion.get()}
                -DANDROID_STL=c++_shared
            )
            set(VCPKG_CMAKE_CONFIGURE_OPTIONS_RELEASE -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON)
            set(VCPKG_C_FLAGS_RELEASE "-Oz -flto=thin")
            set(VCPKG_CXX_FLAGS_RELEASE "-Oz -flto=thin")

            if((PORT STREQUAL "libiconv") OR (PORT STREQUAL "libidn2"))
                set(VCPKG_C_FLAGS "${'$'}{VCPKG_C_FLAGS} -U_FORTIFY_SOURCE")
                set(VCPKG_CXX_FLAGS "${'$'}{VCPKG_CXX_FLAGS} -U_FORTIFY_SOURCE")
            endif()

            # Needed for Qt port
            set(ANDROID_SDK_ROOT "${'$'}ENV{ANDROID_SDK_HOME}")
        """.trimIndent()
        for (triplet in androidTriplets.get()) {
            writeOverlayTriplet(
                triplet,
                overlayTripletsDir,
                appendToAndroid
            )
        }

        val appendToHost = """
            # MODIFIED
            set(VCPKG_BUILD_TYPE release)
        """.trimIndent()
        writeOverlayTriplet(
            hostTriplet.get(),
            overlayTripletsDir,
            appendToHost
        )

        didWork = true
    }

    private fun writeOverlayTriplet(originalTripletPath: Path, overlayTripletsDir: Path, textToAppend: String) {
        val originalTripletBytes = originalTripletPath.readBytes()
        val overlayTripletBytes = (originalTripletBytes.decodeToString() + textToAppend).toByteArray()
        val overlayTripletPath = overlayTripletsDir.resolve(originalTripletPath.fileName)
        overlayTripletPath.writeBytes(overlayTripletBytes)
        logger.lifecycle("Generated overlay triplet {}", overlayTripletPath)
    }
}