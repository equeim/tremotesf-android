#!/bin/bash

_DIR="$(realpath $(dirname $0))"
cd "$_DIR/openssl" || exit 1

case "$ANDROID_ARCH" in
    "armeabi-v7a")
        export _ANDROID_EABI="arm-linux-androideabi-4.9"
        export _ANDROID_ARCH="arch-arm"
    ;;
    "x86")
        export _ANDROID_EABI="x86-4.9"
        export _ANDROID_ARCH="arch-x86"
    ;;
esac

source "$_DIR/setenv-android.sh"
make clean
_PREFIX="$_DIR/install-$ANDROID_ARCH"
./config shared no-ssl2 no-ssl3 no-comp no-hw no-engine --prefix="$_PREFIX"  || exit 1

# 1.0
make depend $MAKEOPTS  || exit 1
make all $MAKEOPTS  || exit 1
make install_sw $MAKEOPTS  || exit 1

# 1.1
#make build_libs $MAKEOPTS
#make install_dev $MAKEOPTS

rm -f "$_PREFIX/lib/"*.so*
