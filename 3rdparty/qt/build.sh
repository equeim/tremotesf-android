#!/bin/bash

readonly QT_DIR="$(realpath -- "$(dirname -- "$0")")"
readonly QT_SOURCE_DIR="$QT_DIR/qtbase"

readonly QT_5_12_VERSION="5.12.10"
readonly QT_5_15_VERSION="5.15.2"

latest_change_file=$(basename $(ls "$QT_SOURCE_DIR"/dist/changes-* | sort --version-sort | tail -n1))
if [[ $latest_change_file = "changes-$QT_5_15_VERSION" ]]; then
    readonly QT_5_15=true
elif [[ $latest_change_file = "changes-$QT_5_12_VERSION" ]]; then
    readonly QT_5_15=false
else
    echo "Unsupported Qt version $latest_change_file"
    exit 1
fi
unset latest_change_file


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
    if patch -p1 -R --dry-run --force --fuzz=0 --input="../patches/$patch" > /dev/null; then
        echo 'Already applied'
    else
        local -r output="$(patch -p1 --fuzz=0 --input="../patches/$patch")" code="$?"
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
    patch_if_needed o2.patch true

    if [ "$QT_5_15" = true ]; then
        # Qt 5.15

        patch_if_needed 5.15/donottryondemand.patch true
        patch_if_needed 5.15/qsslcertificate.patch true
        patch_if_needed 5.15/qtMainLoopThread.patch true

        # Needed to build Qt for 32-bit architectures separately
        patch_if_needed 5.15/default-arch.patch true
    else
        # Qt 5.12 and older

        patch_if_needed 5.12/java7.patch true
        patch_if_needed 5.12/donottryondemand.patch true
        patch_if_needed 5.12/qtMainLoopThread.patch true
        patch_if_needed 5.12/android-platform.patch true

        # NEON fix
        patch_if_needed 5.12/fp16.patch false

        # NDK r19 toolchain
        patch_if_needed 5.12/ndk-r19.patch true

        # LTO
        patch_if_needed 5.12/thin-lto.patch true
    fi

    # LTO
    patch_if_needed ltcg-armv7.patch true
    patch_if_needed openssl-test-ltcg.patch true
}

function join_by { local IFS="$1"; shift; echo "$*"; }
function get_first { echo "$1"; }

function build_515() {
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

    echo
}

if [ ! -d "$QT_SOURCE_DIR" ] ; then
    echo "Qt source directory $QT_SOURCE_DIR does not exist"
    exit 1
fi

if [[ "$QT_5_15" = true ]]; then
    "$TOP_DIR/3rdparty/openssl/build.sh" true || exit 1
else
    "$TOP_DIR/3rdparty/openssl/build.sh" false || exit 1
fi

apply_patches

if [ "$QT_5_15" = true ]; then
    build_515 "$ANDROID_ABIS_32" "$ANDROID_API_32" || exit 1
    build_515 "$ANDROID_ABIS_64" "$ANDROID_API_64" || exit 1
else
    for abi in $ANDROID_ABIS_32; do
        build_512 "$abi" "$ANDROID_API_32" || exit 1
    done

    for abi in $ANDROID_ABIS_64; do
        build_512 "$abi" "$ANDROID_API_64" || exit 1
    done
fi
