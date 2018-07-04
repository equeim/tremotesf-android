#!/bin/bash

_DIR="$(realpath $(dirname $0))"

case "$ANDROID_ARCH" in
    "armeabi-v7a")
        export _ANDROID_EABI="arm-linux-androideabi-4.9"
        export _ANDROID_ARCH="arch-arm"
        export _TRIPLE="arm-linux-androideabi"
    ;;
    "x86")
        export _ANDROID_EABI="x86-4.9"
        export _ANDROID_ARCH="arch-x86"
        export _TRIPLE="i686-linux-android"
    ;;
esac

source "$_DIR/setenv-android.sh"

_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR" || exit 1
cd "$_BUILD_DIR" || exit 1

_PREFIX="$_DIR/install-$ANDROID_ARCH"
../openssl/config shared no-ssl2 no-ssl3 no-comp no-hw no-engine --prefix="$_PREFIX" -D__ANDROID_API__=16 -isystem"$ANDROID_NDK_ROOT/sysroot/usr/include" -isystem"$ANDROID_NDK_ROOT/sysroot/usr/include/$_TRIPLE" || exit 1

make build_libs $MAKEOPTS || exit 1
make install_dev $MAKEOPTS || exit 1

rm -f "$_PREFIX/lib/"*.so*
