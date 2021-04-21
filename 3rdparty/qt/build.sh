#!/bin/bash

readonly QT_DIR="$(dirname -- "$(realpath -s -- "$0")")"
readonly QT_SOURCE_DIR="$QT_DIR/qtbase"

readonly OPENSSL_DIR="$(realpath -s -- "$QT_DIR/../openssl")"
readonly OPENSSL_INCDIR="$OPENSSL_DIR/install/include"
readonly OPENSSL_LIBDIR="$OPENSSL_DIR/install/lib"

readonly PATCHES=(
    qmake-makeopts.patch
    o2.patch

    # LTO
    openssl-test-ltcg.patch

    qsslsocket-qdiriterator.patch
    qsslcertificate.patch
    qtMainLoopThread.patch

    ndk-r22.patch

    # Needed to build Qt for 32-bit ABIs targeting API 16
    default-arch.patch
)

readonly CONFIGURE_FLAGS=(
    '-v'
    '-confirm-license'
    '-opensource'
    '-xplatform' 'android-clang'
    '-c++std' 'c++1z'
    '-android-ndk' "$ANDROID_NDK_ROOT"
    '-android-sdk' "$ANDROID_SDK_ROOT"
    '-android-ndk-host' 'linux-x86_64'
    '-linker' 'lld'
    '-ltcg'
    '-no-use-gold-linker'
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
    "-I$OPENSSL_INCDIR"
    "-L$OPENSSL_LIBDIR"
)

source "$QT_DIR/../lib.sh" || exit 1

function join_by { local IFS="$1"; shift; echo "$*"; }
function get_first { echo "$1"; }

function build() {
    local -r abis="$1"
    local -r api="$2"

    echo "Building Qt for $abis, API $api"
    echo

    local -r build_dir="$QT_DIR/build-api$api"
    mkdir -p "$build_dir" || return 1
    cd "$build_dir" || return 1

    local -r prefix="$QT_DIR/install-api$api"
    local flags=("${CONFIGURE_FLAGS[@]}")
    flags+=(
        '-prefix' "$prefix"
        '-android-abis' "$(join_by ',' $abis)"
        '-android-ndk-platform' "android-$api"
    )

    local -r first_abi=$(get_first $abis)
    export OPENSSL_LIBS="-lssl_$first_abi -lcrypto_$first_abi"
    "$QT_DIR/qtbase/configure" "${flags[@]}" || return 1
    unset OPENSSL_LIBS

    make $MAKEOPTS || return 1
    make install $MAKEOPTS || return 1

    echo
}

if [[ ! -d "$QT_SOURCE_DIR" ]]; then
    echo "Qt source directory $QT_SOURCE_DIR does not exist"
    exit 1
fi

"$OPENSSL_DIR/build.sh" || exit 1

cd "$QT_SOURCE_DIR" || exit 1
apply_patches "${PATCHES[@]}" || exit 1

build "$ANDROID_ABIS_32" "$ANDROID_API_32" || exit 1
build "$ANDROID_ABIS_64" "$ANDROID_API_64" || exit 1
