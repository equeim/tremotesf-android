#!/bin/bash

readonly OPENSSL_DIR="$(dirname -- "$(realpath -s -- "$0")")"
readonly OPENSSL_SOURCE_DIR="${OPENSSL_DIR}/openssl"

source "$OPENSSL_DIR/../lib.sh" || exit 1

function build() {
    local -r abi="$1"
    local -r api="$2"

    echo "Building OpenSSL for $abi API $api"

    local -r build_dir="$OPENSSL_DIR/build-$abi"
    mkdir -p "$build_dir" || return 1
    cd "$build_dir" || return 1

    local cflags=('-fvisibility=hidden' '-fvisibility-inlines-hidden' '-O2' '-flto=thin' "-D__ANDROID_API__=$api")
    case "$abi" in
        'armeabi-v7a')
            local -r target='android-arm'
            cflags+=('-mfpu=neon')
        ;;
        'x86')
            local -r target='android-x86'
        ;;
        'arm64-v8a')
            local -r target='android-arm64'
        ;;
        'x86_64')
            local -r target='android-x86_64'
        ;;
        *)
            return 1
        ;;
    esac

    local -r prefix="$OPENSSL_DIR/install"

    export ANDROID_NDK="$ANDROID_NDK_ROOT"
    "$OPENSSL_SOURCE_DIR/Configure" "$target" no-shared no-ssl3 no-comp no-hw no-engine --prefix="$prefix" "${cflags[@]}" || return 1
    unset ANDROID_NDK

    make build_libs $MAKEOPTS || return 1
    make install_dev $MAKEOPTS || return 1

    mv "$prefix/lib/libcrypto.a" "$prefix/lib/libcrypto_$abi.a" || return 1
    mv "$prefix/lib/libssl.a" "$prefix/lib/libssl_$abi.a" || return 1

    echo
}

cd "${OPENSSL_SOURCE_DIR}" || exit 1

apply_patches ndk-r22.patch poly1305-armv4.patch || exit 1

for abi in $ANDROID_ABIS_32; do
    build "$abi" "$ANDROID_API_32" || exit 1
done

for abi in $ANDROID_ABIS_64; do
    build "$abi" "$ANDROID_API_64" || exit 1
done
