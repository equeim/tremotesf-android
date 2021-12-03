cmake_minimum_required(VERSION 3.10.2)

project(tremotesf CXX)

list(APPEND CMAKE_FIND_ROOT_PATH "${OPENSSL_DIR}/install-${ANDROID_ABI}")
list(APPEND CMAKE_FIND_ROOT_PATH "${QT_DIR}/install-${ANDROID_ABI}")

set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

find_library(log-lib log)

find_package(Qt6 COMPONENTS Core Concurrent Network REQUIRED)
set(Qt6QTlsBackendOpenSSLPlugin_DIR "${Qt6Network_DIR}")
find_package(Qt6QTlsBackendOpenSSLPlugin REQUIRED)

set(
        sources
        libtremotesf/peer.cpp
        libtremotesf/rpc.cpp
        libtremotesf/serversettings.cpp
        libtremotesf/serverstats.cpp
        libtremotesf/torrent.cpp
        libtremotesf/torrentfile.cpp
        libtremotesf/tracker.cpp
        jnirpc.cpp
)

add_library(
        ${PROJECT_NAME} SHARED
        ${sources}
        libtremotesf_wrap.cxx
        resources.qrc
)

set_target_properties(
        ${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
        INTERPROCEDURAL_OPTIMIZATION ON
)

set(
        warnings
        -Wall
        -Wextra
        -Wpedantic
        -Wnon-virtual-dtor
        -Wcast-align
        -Woverloaded-virtual
        -Wconversion
        -Wsign-conversion
        -Wdouble-promotion
        -Wformat=2
        -Werror=format
)
set_source_files_properties(${sources} PROPERTIES COMPILE_OPTIONS "${warnings}")

target_compile_definitions(${PROJECT_NAME} PRIVATE QT_DEPRECATED_WARNINGS QT_DISABLE_DEPRECATED_BEFORE=0x05f00)
target_include_directories(${PROJECT_NAME} PRIVATE ${Qt6Concurrent_INCLUDE_DIRS})
target_link_libraries(${PROJECT_NAME} ${log-lib} Qt6::Core Qt6::Network Qt6::QTlsBackendOpenSSLPlugin)

# CMake overrides linker with -fuse-ld=gold when INTERPROCEDURAL_OPTIMIZATION is enabled. Bring it back
# We are using target_link_libraries instead of target_link_options
# because CMake adds -fuse-ld=gold at the end of link flags, so we can't override it there
# target_link_libraries flags, on the other hand, are added after target_link_options, so it works
target_link_libraries(${PROJECT_NAME} -fuse-ld=lld)
