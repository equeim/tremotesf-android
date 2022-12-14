cmake_minimum_required(VERSION 3.22.1)
cmake_policy(VERSION ${CMAKE_MINIMUM_REQUIRED_VERSION}..3.25)

set(CMAKE_TOOLCHAIN_FILE "${QT_DIR}/install-${ANDROID_ABI}/lib/cmake/Qt6/qt.toolchain.cmake")
list(APPEND CMAKE_FIND_ROOT_PATH "${OPENSSL_DIR}/install-${ANDROID_ABI}")

project(tremotesf CXX)

set(CMAKE_INTERPROCEDURAL_OPTIMIZATION ON)

find_library(log-lib log)

find_package(Qt6 COMPONENTS Core Concurrent Network REQUIRED)
set(Qt6QTlsBackendOpenSSLPlugin_DIR "${Qt6Network_DIR}")
find_package(Qt6QTlsBackendOpenSSLPlugin REQUIRED)

add_subdirectory(fmt EXCLUDE_FROM_ALL)

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
