cmake_minimum_required(VERSION 3.18.1)

set(CMAKE_TOOLCHAIN_FILE "${QT_DIR}/install-${ANDROID_ABI}/lib/cmake/Qt6/qt.toolchain.cmake")
list(APPEND CMAKE_FIND_ROOT_PATH "${OPENSSL_DIR}/install-${ANDROID_ABI}")

project(tremotesf CXX)

set(CMAKE_INTERPROCEDURAL_OPTIMIZATION ON)

find_library(log-lib log)

find_package(Qt6 COMPONENTS Core Concurrent Network REQUIRED)
set(Qt6QTlsBackendOpenSSLPlugin_DIR "${Qt6Network_DIR}")
find_package(Qt6QTlsBackendOpenSSLPlugin REQUIRED)

add_subdirectory(fmt)

set(TREMOTESF_QT6 ON CACHE BOOL "Build with Qt 6")
set(TREMOTESF_SAILFISHOS OFF CACHE BOOL "Build for Sailfish OS")
include(libtremotesf/cmake/CommonOptions.cmake)
add_subdirectory(libtremotesf)

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

# CMake overrides linker with -fuse-ld=gold when INTERPROCEDURAL_OPTIMIZATION is enabled. Bring it back
# We are using target_link_libraries instead of target_link_options
# because CMake adds -fuse-ld=gold at the end of link flags, so we can't override it there
# target_link_libraries flags, on the other hand, are added after target_link_options, so it works
if (CMAKE_VERSION VERSION_LESS "3.20.0")
    target_link_libraries(${PROJECT_NAME} -fuse-ld=lld)
endif()

set_common_options_on_targets()
