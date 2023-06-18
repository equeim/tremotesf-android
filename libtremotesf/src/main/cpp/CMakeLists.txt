# SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
#
# SPDX-License-Identifier: CC0-1.0

cmake_minimum_required(VERSION 3.22.1)
cmake_policy(VERSION ${CMAKE_MINIMUM_REQUIRED_VERSION}..3.26)

set(VCPKG_CHAINLOAD_TOOLCHAIN_FILE "${ANDROID_NDK}/build/cmake/android.toolchain.cmake")

message(STATUS "ANDROID_ABI is ${ANDROID_ABI}")
if (ANDROID_ABI MATCHES "arm64-v8a")
    set(VCPKG_TARGET_TRIPLET arm64-android)
elseif(ANDROID_ABI MATCHES "armeabi-v7a")
    set(VCPKG_TARGET_TRIPLET arm-neon-android)
elseif(ANDROID_ABI MATCHES "x86_64")
    set(VCPKG_TARGET_TRIPLET x64-android)
elseif(ANDROID_ABI MATCHES "x86")
    set(VCPKG_TARGET_TRIPLET x86-android)
else()
    message(FATAL_ERROR "Unknown ANDROID_ABI ${ANDROID_ABI}")
endif()

message(STATUS "VCPKG_TARGET_TRIPLET is ${VCPKG_TARGET_TRIPLET}")
set(VCPKG_INSTALLED_DIR "${TREMOTESF_VCPKG_INSTALLED_DIR_PREFIX}${VCPKG_TARGET_TRIPLET}")
message(STATUS "VCPKG_INSTALLED_DIR is ${VCPKG_INSTALLED_DIR}")

project(tremotesf CXX)

set(CMAKE_INTERPROCEDURAL_OPTIMIZATION ON)

find_library(log-lib log)

find_package(Qt6 COMPONENTS Core Concurrent Network REQUIRED)
set(Qt6QTlsBackendOpenSSLPlugin_DIR "${Qt6Network_DIR}")
find_package(Qt6QTlsBackendOpenSSLPlugin REQUIRED)

set(TREMOTESF_QT6 ON CACHE BOOL "Build with Qt 6")
include(libtremotesf/cmake/CommonOptions.cmake)
add_subdirectory(libtremotesf EXCLUDE_FROM_ALL)

add_library(
        ${PROJECT_NAME} SHARED
        jnirpc.cpp
        libtremotesf_wrap.cxx
        resources.qrc
)

set_target_properties(
        ${PROJECT_NAME} PROPERTIES
        INCLUDE_CURRENT_DIR ON
        AUTOMOC ON
        AUTORCC ON
)

set_source_files_properties(libtremotesf_wrap.cxx PROPERTIES COMPILE_OPTIONS "-Wno-sign-conversion")

target_link_libraries(${PROJECT_NAME} libtremotesf "${log-lib}" Qt6::QTlsBackendOpenSSLPlugin)

set_common_options_on_targets()
