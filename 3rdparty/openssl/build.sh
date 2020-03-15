#!/bin/bash

readonly OPENSSL_DIR="$(realpath -- "$(dirname -- "$0")")"

readonly ADD_ABI_SUFFIX="$1"

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
            cflags+=('-march=armv7-a' '-mfpu=neon')
        ;;
        'x86')
            local -r target='android-x86'
            cflags+=('-mstackrealign')
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

    if [ "$ADD_ABI_SUFFIX" = true ]; then
        local -r prefix="$OPENSSL_DIR/install"
    else
        local -r prefix="$OPENSSL_DIR/install-$abi"
    fi

    export ANDROID_NDK="$ANDROID_NDK_ROOT"
    export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
    "$OPENSSL_DIR/openssl/Configure" "$target" no-shared no-ssl3 no-comp no-hw no-engine --prefix="$prefix" "${cflags[@]}" || return 1

    make build_libs $MAKEOPTS || return 1
    make install_dev $MAKEOPTS || return 1

    if [ "$ADD_ABI_SUFFIX" = true ]; then
        mv "$prefix/lib/libcrypto.a" "$prefix/lib/libcrypto_$abi.a" || return 1
        mv "$prefix/lib/libssl.a" "$prefix/lib/libssl_$abi.a" || return 1
    fi

    echo
}

for abi in $ANDROID_ABIS_32; do
    build "$abi" "$ANDROID_API_32" || exit 1
done

for abi in $ANDROID_ABIS_64; do
    build "$abi" "$ANDROID_API_64" || exit 1
done
