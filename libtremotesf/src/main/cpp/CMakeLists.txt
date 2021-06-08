cmake_minimum_required(VERSION 3.10.2)

project(tremotesf CXX)

list(APPEND CMAKE_FIND_ROOT_PATH "${QT_DIR}/install-api${ANDROID_PLATFORM_LEVEL}")

string(TOLOWER ${CMAKE_BUILD_TYPE} build_type)
if ((NOT DEFINED LIBTREMOTESF_FLAVOR) OR (LIBTREMOTESF_FLAVOR STREQUAL ""))
    set(generated_sources_dir "${GENERATED_SOURCES_BASE_DIR}/${build_type}")
else()
    set(generated_sources_dir "${GENERATED_SOURCES_BASE_DIR}/${LIBTREMOTESF_FLAVOR}/${build_type}")
endif()

set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CMAKE_AUTOMOC ON)

find_library(log-lib log)

find_package(Qt5Core REQUIRED)
find_package(Qt5Concurrent REQUIRED)
find_package(Qt5Network REQUIRED)

set(libtremotesf_sources
        libtremotesf/peer.cpp
        libtremotesf/rpc.cpp
        libtremotesf/serversettings.cpp
        libtremotesf/serverstats.cpp
        libtremotesf/torrent.cpp
        libtremotesf/torrentfile.cpp
        libtremotesf/tracker.cpp
        jnirpc.cpp)

set(generated_sources
        ${generated_sources_dir}/jnijavacpp.cpp
        ${generated_sources_dir}/jniLibTremotesf.cpp)

add_library(${PROJECT_NAME} SHARED ${libtremotesf_sources} ${generated_sources})

target_compile_definitions(${PROJECT_NAME} PRIVATE QT_DEPRECATED_WARNINGS QT_DISABLE_DEPRECATED_BEFORE=0x05f00)

target_compile_options(${PROJECT_NAME} PRIVATE
        -Wall
        -Wextra
        -Wpedantic
        -Wnon-virtual-dtor
        -Woverloaded-virtual
        -Wdouble-promotion
        -Wformat=2
        -Werror=format)

set(libtremotesf_compile_options
        -Wcast-align
        -Wconversion
        -Wsign-conversion)
set_source_files_properties(${libtremotesf_sources} PROPERTIES COMPILE_OPTIONS "${libtremotesf_compile_options}")

set(generated_compile_options
        -Wno-unused-parameter
        -Wno-unused-variable
        -Wno-vla-extension
        -Wno-format-nonliteral)
set_source_files_properties(${generated_sources} PROPERTIES COMPILE_OPTIONS "${generated_compile_options}")

target_include_directories(${PROJECT_NAME} PRIVATE ${Qt5Concurrent_INCLUDE_DIRS})
target_link_libraries(${PROJECT_NAME} ${log-lib} Qt5::Core Qt5::Network)
# CMake overrides linker with -fuse-ld=gold when INTERPROCEDURAL_OPTIMIZATION is enabled. Bring it back
# We are using target_link_libraries instead of target_link_options
# because CMake adds -fuse-ld=gold at the end of link flags, so we can't override it there
# target_link_libraries flags, on the other hand, are added after target_link_options, so it works
target_link_libraries(${PROJECT_NAME} -fuse-ld=lld)

set_target_properties(${PROJECT_NAME} PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
        INTERPROCEDURAL_OPTIMIZATION_RELEASE ON)
