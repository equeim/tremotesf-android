// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.gradle.tasks

import org.equeim.tremotesf.gradle.utils.ANDROID_TRIPLETS
import org.equeim.tremotesf.gradle.utils.HOST_TRIPLET
import org.equeim.tremotesf.gradle.utils.overlayTripletsDir
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.writeText

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

    @get:OutputDirectory
    protected val overlayTripletsDir: Provider<Path> by lazy { overlayTripletsDir(projectLayout) }

    @TaskAction
    fun generateOverlayTriplets() {
        logger.lifecycle("Generating overlay triplets")

        val overlayTripletsDir = this.overlayTripletsDir.get()
        Files.createDirectories(overlayTripletsDir)

        for (triplet in ANDROID_TRIPLETS) {
            val text = """
                include("${'$'}{VCPKG_ROOT_DIR}/triplets/community/${triplet}.cmake")

                set(VCPKG_BUILD_TYPE release)
                set(VCPKG_CRT_LINKAGE dynamic)
                # vcpkg's android toolchain file uses it to set ANDROID_PLATFORM
                set(VCPKG_CMAKE_SYSTEM_VERSION ${minSdkVersion.get()})
                set(
                    VCPKG_CMAKE_CONFIGURE_OPTIONS
                    # Fix CMake forcing gold linker. Remove when CMake in SDK is updated to 3.25.3/3.26
                    -DCMAKE_ANDROID_NDK_VERSION=${ndkVersionMajor.get()}
                )
                set(VCPKG_CMAKE_CONFIGURE_OPTIONS_RELEASE -DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON)
                set(VCPKG_C_FLAGS_RELEASE "-Oz -flto=thin")
                set(VCPKG_CXX_FLAGS_RELEASE "-Oz -flto=thin")

                if((PORT STREQUAL "libiconv") OR (PORT STREQUAL "libidn2"))
                    set(VCPKG_C_FLAGS "${'$'}{VCPKG_C_FLAGS} -U_FORTIFY_SOURCE")
                    set(VCPKG_CXX_FLAGS "${'$'}{VCPKG_CXX_FLAGS} -U_FORTIFY_SOURCE")
                endif()

                if(PORT STREQUAL qtbase-tremotesf-android)
                    set(ANDROID_SDK_ROOT "${'$'}ENV{ANDROID_SDK_HOME}")
                    list(APPEND VCPKG_CMAKE_CONFIGURE_OPTIONS
                        # Qt's CMake files intercept these values before vcpkg's toolchain file sets them,
                        # so set them manually in advance
                        -DANDROID_PLATFORM=${minSdkVersion.get()}
                        -DANDROID_NATIVE_API_LEVEL=${minSdkVersion.get()}
                        -DANDROID_STL=c++_shared
                    )
                endif()
            """.trimIndent()
            writeOverlayTriplet(overlayTripletsDir.resolve("${triplet}.cmake"), text)
        }

        val hostText = """
            include("${'$'}{VCPKG_ROOT_DIR}/triplets/${HOST_TRIPLET}.cmake")
            set(VCPKG_BUILD_TYPE release)
        """.trimIndent()
        writeOverlayTriplet(overlayTripletsDir.resolve("${HOST_TRIPLET}.cmake"), hostText)

        didWork = true
    }

    private fun writeOverlayTriplet(tripletPath: Path, tripletText: String) {
        tripletPath.writeText(tripletText)
        logger.lifecycle("Generated overlay triplet {}", tripletPath)
    }
}
