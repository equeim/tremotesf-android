#!/bin/bash

_DIR="$(realpath $(dirname $0))"

case "$ANDROID_ARCH" in
    "armeabi-v7a")
        export _TARGET="android-arm"
    ;;
    "x86")
        export _TARGET="android-x86"
    ;;
esac

_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR" || exit 1
cd "$_BUILD_DIR" || exit 1

_PREFIX="$_DIR/install-$ANDROID_ARCH"
export ANDROID_NDK="$ANDROID_NDK_ROOT"
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
../openssl/Configure $_TARGET shared no-ssl3 no-comp no-hw no-engine --prefix="$_PREFIX" -D__ANDROID_API__=16 || exit 1

make build_libs $MAKEOPTS || exit 1
make install_dev $MAKEOPTS || exit 1

rm -f "$_PREFIX/lib/"*.so*
