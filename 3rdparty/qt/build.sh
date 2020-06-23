#!/bin/bash

readonly QT_DIR="$(realpath -- "$(dirname -- "$0")")"
readonly QT_SOURCE_DIR="$QT_DIR/qtbase"

test -f "$QT_SOURCE_DIR/dist/changes-5.12.0"
readonly HAS_5_12=$?

test -f "$QT_SOURCE_DIR/dist/changes-5.14.0"
readonly HAS_5_14=$?

test -f "$QT_SOURCE_DIR/dist/changes-5.15.0"
readonly HAS_5_15=$?

readonly COMMON_FLAGS=(
    '-v'
    '-confirm-license'
    '-opensource'
    '-xplatform' 'android-clang'
    '-c++std' 'c++1z'
    '-android-ndk' "$ANDROID_NDK_ROOT"
    '-android-sdk' "$ANDROID_SDK_ROOT"
    '-android-ndk-host' 'linux-x86_64'
    '-ltcg'
    '-nomake' 'examples'
    '-nomake' 'tests'
    '-no-dbus'
    '-no-gui'
    '-no-feature-animation'
    '-no-feature-bearermanagement'
    '-no-feature-big_codecs'
    '-no-feature-codecs'
    '-no-feature-commandlineparser'
    '-no-feature-datetimeparser'
    '-no-feature-dnslookup'
    '-no-feature-dom'
    '-no-feature-dtls'
    '-no-feature-filesystemiterator'
    '-no-feature-filesystemwatcher'
    '-no-feature-ftp'
    '-no-feature-itemmodel'
    '-no-feature-localserver'
    '-no-feature-mimetype'
    '-no-feature-networkdiskcache'
    '-no-feature-process'
    '-no-feature-processenvironment'
    '-no-feature-settings'
    '-no-feature-sql'
    '-no-feature-sharedmemory'
    '-no-feature-statemachine'
    '-no-feature-systemsemaphore'
    '-no-feature-temporaryfile'
    '-no-feature-testlib'
    '-no-feature-textcodec'
    '-no-feature-translation'
    '-no-feature-udpsocket'
    '-no-feature-xml'
    '-no-feature-xmlstream'
    '-no-feature-regularexpression'
    '-openssl-linked'
)

function patch_if_needed() {
    # if can't reverse, patch
    local -r patch="$1"
    local -r fail_on_failure="$2"
    echo "Applying patch $patch"
    if patch -p1 -R --dry-run --force --fuzz=0 --input="../$patch" > /dev/null; then
        echo 'Already applied'
    else
        local -r output="$(patch -p1 --fuzz=0 --input="../$patch")" code="$?"
        if [ "$code" -ne 0 ]; then
            printf '%s\n' "$output"
            if [ "$fail_on_failure" = true ]; then
                echo 'Failed to apply patch, exiting'
                exit 1
            fi
            echo 'Failed to apply patch, continuing'
        else
            echo 'Applied'
        fi
    fi
    echo
}

function apply_patches() {
    cd "$QT_SOURCE_DIR" || return 1

    patch_if_needed qmakemake.patch false
    patch_if_needed java7.patch true
    patch_if_needed o2.patch true

    if [ "$HAS_5_15" -eq 0 ]; then
        # Qt 5.15

        patch_if_needed donottryondemand_qt5.15.patch true
        patch_if_needed qsslcertificate.patch true
    else
        # Qt 5.14 and older

        patch_if_needed donottryondemand.patch true

        # NEON fix
        patch_if_needed fp16.patch false
    fi

    if [ "$HAS_5_14" -eq 0 ]; then
        # Qt 5.14 and newer
        # Needed to build Qt for 32-bit architectures separately
        patch_if_needed default-arch.patch true
    else
        # Qt 5.13 and older

        # NDK r19 toolchain
        patch_if_needed libc++.patch false
        patch_if_needed mips.patch false
        patch_if_needed ndk-r19.patch true

        # LTO
        patch_if_needed thin-lto.patch true
    fi

    # LTO
    patch_if_needed ltcg-armv7.patch true
    patch_if_needed openssl-test-ltcg.patch true
}

function join_by { local IFS="$1"; shift; echo "$*"; }
function get_first { echo "$1"; }

function build_514() {
    local -r abis="$1"
    local -r api="$2"

    echo "Building Qt for $abis, API $api"
    echo

    local -r build_dir="$QT_DIR/build-api$api"
    mkdir -p "$build_dir" || return 1
    cd "$build_dir" || return 1

    local -r openssl_libdir="$QT_DIR/../openssl/install/lib"
    local -r openssl_incdir="$QT_DIR/../openssl/install/include"

    local -r prefix="$QT_DIR/install-api$api"
    local flags=("${COMMON_FLAGS[@]}")
    flags+=(
        '-prefix' "$prefix"
        '-linker' 'lld'
        '-android-abis' "$(join_by ',' $abis)"
        '-android-ndk-platform' "android-$api"
        "-I$openssl_incdir"
        "-L$openssl_libdir"
    )

    # OPENSSL_LIBS is used only for tests
    #export OPENSSL_LIBS="-L${openssl_libdir}_$abi -lssl -lcrypto"
    local -r first_abi=$(get_first $abis)
    export OPENSSL_LIBS="-lssl_$first_abi -lcrypto_$first_abi"
    "$QT_DIR/qtbase/configure" "${flags[@]}" || return 1

    make $MAKEOPTS || return 1
    make install $MAKEOPTS || return 1

    for abi in $abis; do
        local libs="$TOP_DIR/app/libs/$abi"
        mkdir -p "$libs" || return 1
        cp "$prefix/lib/libQt5Core_$abi.so" "$libs" || return 1
        cp "$prefix/lib/libQt5Network_$abi.so" "$libs" || return 1
    done

    echo
}

function build_512() {
    local -r abi="$1"
    local -r api="$2"

    echo "Building Qt for $abi, API $api"
    echo

    local -r build_dir="$QT_DIR/build-$abi"
    mkdir -p "$build_dir" || return 1
    cd "$build_dir" || return 1

    local -r openssl_libdir="$QT_DIR/../openssl/install-$abi/lib"
    local -r openssl_incdir="$QT_DIR/../openssl/install-$abi/include"

    local -r prefix="$QT_DIR/install-$abi"

    local flags=("${COMMON_FLAGS[@]}")
    flags+=(
        '-prefix' "$prefix"
        '-android-arch' "$abi"
        '-android-ndk-platform' "android-$api"
        "-I$openssl_incdir"
        "-L$openssl_libdir"
    )

    "$QT_DIR/qtbase/configure" "${flags[@]}" || return 1

    make $MAKEOPTS || return 1
    make install $MAKEOPTS || return 1

    local -r libs="$TOP_DIR/app/libs/$abi"
    mkdir -p "$libs" || return 1

    cp "$prefix/lib/libQt5Core.so" "$libs" || return 1
    cp "$prefix/lib/libQt5Network.so" "$libs" || return 1

    echo
}

if [ ! -d "$QT_SOURCE_DIR" ] ; then
    echo "Qt source directory $QT_SOURCE_DIR does not exist"
    exit 1
fi

if [ "$HAS_5_12" -ne 0 ]; then
    echo 'Minimum Qt version is 5.12.0, aborting'
    exit 1
fi

if [ "$HAS_5_14" -eq 0 ]; then
    "$TOP_DIR/3rdparty/openssl/build.sh" true || exit 1
else
    "$TOP_DIR/3rdparty/openssl/build.sh" false || exit 1
fi

apply_patches

if [ "$HAS_5_14" -eq 0 ]; then
    build_514 "$ANDROID_ABIS_32" "$ANDROID_API_32"
    build_514 "$ANDROID_ABIS_64" "$ANDROID_API_64"
    cp "$QT_DIR/install-api$ANDROID_API_32/jar/QtAndroid.jar" "$TOP_DIR/app/libs/" || exit 1
else
    for abi in $ANDROID_ABIS_32; do
        build_512 "$abi" "$ANDROID_API_32" || exit 1
    done

    for abi in $ANDROID_ABIS_64; do
        build_512 "$abi" "$ANDROID_API_64" || exit 1
    done

    cp "$QT_DIR/install-$(get_first $ANDROID_ABIS_32)/jar/QtAndroid.jar" "$TOP_DIR/app/libs/" || exit 1
fi
