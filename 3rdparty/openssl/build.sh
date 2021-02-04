#!/bin/bash

readonly ADD_ABI_SUFFIX="$1"

readonly OPENSSL_DIR="$(dirname -- "$(realpath -s -- "$0")")"
readonly OPENSSL_SOURCE_DIR="${OPENSSL_DIR}/openssl"

source "$OPENSSL_DIR/../lib.sh" || exit 1

readonly OPENSSL_VERSION="0x1010109fL"

version="$(echo OPENSSL_VERSION_NUMBER | cpp -imacros "$OPENSSL_SOURCE_DIR/include/openssl/opensslv.h" - | tail -n1)"
if [[ "$version" != "$OPENSSL_VERSION" ]]; then
    echo "Unsupport OpenSSL version $version"
    exit 1
fi
unset version

function build() {
    local -r abi="$1"
    local -r api="$2"

    echo "Building OpenSSL for $abi API $api"

    local -r build_dir="$OPENSSL_DIR/build-$abi"
    mkdir -p "$build_dir" || exit 1
    cd "$build_dir" || exit 1

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
            exit 1
        ;;
    esac

    if [[ "$ADD_ABI_SUFFIX" = true ]]; then
        local -r prefix="$OPENSSL_DIR/install"
    else
        local -r prefix="$OPENSSL_DIR/install-$abi"
    fi

    export ANDROID_NDK="$ANDROID_NDK_ROOT"
    "$OPENSSL_SOURCE_DIR/Configure" "$target" no-shared no-ssl3 no-comp no-hw no-engine --prefix="$prefix" "${cflags[@]}" || exit 1

    make build_libs $MAKEOPTS || exit 1
    make install_dev $MAKEOPTS || exit 1

    if [[ "$ADD_ABI_SUFFIX" = true ]]; then
        mv "$prefix/lib/libcrypto.a" "$prefix/lib/libcrypto_$abi.a" || exit 1
        mv "$prefix/lib/libssl.a" "$prefix/lib/libssl_$abi.a" || exit 1
    fi

    echo
}

cd "${OPENSSL_SOURCE_DIR}" || exit 1
patch_if_needed ndk-r22.patch
patch_if_needed poly1305-armv4.patch

for abi in $ANDROID_ABIS_32; do
    build "$abi" "$ANDROID_API_32"
done

for abi in $ANDROID_ABIS_64; do
    build "$abi" "$ANDROID_API_64"
done
