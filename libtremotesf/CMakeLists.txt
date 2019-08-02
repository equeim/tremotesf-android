cmake_minimum_required(VERSION 3.10.2)

project(tremotesf CXX)

list(APPEND CMAKE_FIND_ROOT_PATH "${PROJECT_SOURCE_DIR}/../3rdparty/qt/install-${CMAKE_ANDROID_ARCH_ABI}")

set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)

find_package(Qt5Core REQUIRED)
find_package(Qt5Concurrent REQUIRED)
find_package(Qt5Network REQUIRED)

set(CMAKE_INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)

add_library(${PROJECT_NAME} SHARED
        rpc.cpp
        serversettings.cpp
        serverstats.cpp
        torrent.cpp
        tracker.cpp
        jni/jnirpc.cpp
        jni/libtremotesf_wrap.cxx)

set_target_properties(${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF)

target_compile_definitions(${PROJECT_NAME} PRIVATE QT_DEPRECATED_WARNINGS QT_DISABLE_DEPRECATED_BEFORE=0x051100)
target_compile_options(${PROJECT_NAME} PRIVATE -Wall -Wextra -pedantic)
target_include_directories(${PROJECT_NAME} PRIVATE ${Qt5Concurrent_INCLUDE_DIRS})
target_link_libraries(${PROJECT_NAME} Qt5::Core Qt5::Network)

# When building for armeabi-v7, -Oz is added to CXXFLAGS. When LTO is enabled, it is passed to
# linker flags too, and linker doesn't like -Oz. So we just override it with -O2
target_link_libraries(${PROJECT_NAME} $<$<AND:$<STREQUAL:${CMAKE_ANDROID_ARCH_ABI},armeabi-v7a>,$<CONFIG:RELEASE>>:-O2>)
