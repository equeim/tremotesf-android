#!/bin/bash

echo
echo 'Building OpenSSL for $ANDROID_ARCH'

readonly top_dir=$(realpath $(dirname "$0"))
readonly build_dir="$top_dir/build-$ANDROID_ARCH"
mkdir -p "$build_dir" || exit 1
cd "$build_dir" || exit 1

cflags="-fvisibility=hidden -fvisibility-inlines-hidden -D__ANDROID_API__=$ANDROID_API"
case "$ANDROID_ARCH" in
    'armeabi-v7a')
        readonly target='android-arm'
        cflags+=' -march=armv7-a -mfpu=vfpv3-d16'
    ;;
    'x86')
        readonly target='android-x86'
        cflags+=' -mstackrealign'
    ;;
    'arm64-v8a')
        readonly target="android-arm64"
    ;;
    'x86_64')
        readonly target='android-x86_64'
    ;;
    *)
        exit 1
    ;;
esac

export ANDROID_NDK="$ANDROID_NDK_ROOT"
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
../openssl/Configure $target no-shared no-ssl3 no-comp no-hw no-engine --prefix="$top_dir/install-$ANDROID_ARCH" $cflags || exit 1

make build_libs $MAKEOPTS || exit 1
make install_dev $MAKEOPTS || exit 1
