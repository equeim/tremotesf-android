cmake_minimum_required(VERSION 3.10.2)

project(tremotesf CXX)

if (QT_HAS_ABI_SUFFIX)
    list(APPEND CMAKE_FIND_ROOT_PATH "${PROJECT_SOURCE_DIR}/../3rdparty/qt/install-api${ANDROID_PLATFORM_LEVEL}")
else()
    list(APPEND CMAKE_FIND_ROOT_PATH "${PROJECT_SOURCE_DIR}/../3rdparty/qt/install-${CMAKE_ANDROID_ARCH_ABI}")
endif()

set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt5Core REQUIRED)
find_package(Qt5Concurrent REQUIRED)
find_package(Qt5Network REQUIRED)

add_library(${PROJECT_NAME} SHARED
        peer.cpp
        rpc.cpp
        serversettings.cpp
        serverstats.cpp
        torrent.cpp
        torrentfile.cpp
        tracker.cpp
        jni/jnirpc.cpp
        jni/libtremotesf_wrap.cxx)

set_target_properties(${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
        INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)

target_compile_definitions(${PROJECT_NAME} PRIVATE QT_DEPRECATED_WARNINGS QT_DISABLE_DEPRECATED_BEFORE=0x05f00)
target_compile_options(${PROJECT_NAME} PRIVATE -Wall -Wextra -pedantic)
target_include_directories(${PROJECT_NAME} PRIVATE ${Qt5Concurrent_INCLUDE_DIRS})

target_link_libraries(${PROJECT_NAME} -fuse-ld=lld)

# When building for armeabi-v7, -Oz is added to CXXFLAGS. When LTO is enabled, it is passed to
# linker flags too, and linker doesn't like -Oz. So we just override it with -O2
target_link_libraries(${PROJECT_NAME} $<$<AND:$<STREQUAL:${CMAKE_ANDROID_ARCH_ABI},armeabi-v7a>,$<CONFIG:RELEASE>>:-O2>)

target_link_libraries(${PROJECT_NAME} Qt5::Core Qt5::Network)
