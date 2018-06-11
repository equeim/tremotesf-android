#!/bin/bash

function _build() {
    "$_DIR"/3rdparty/openssl/build.sh || return 1
    "$_DIR"/3rdparty/qt/build.sh || return 1
    "$_DIR"/libtremotesf/build.sh || return 1
    return 0
}

_DIR="$(realpath $(dirname $0))"

export MAKEOPTS="$@"

export ANDROID_ARCH=armeabi-v7a
_build || exit 1

export ANDROID_ARCH=x86
_build || exit 1

_QT="$(realpath $_DIR/3rdparty/qt/install-$ANDROID_ARCH)"
_BUNDLED="$_QT/jar/QtAndroid-bundled.jar"
_LIBS="$_DIR/app/libs/"
if [ -f "$_BUNDLED" ]; then
    cp "$_BUNDLED" "$_LIBS" || exit 1
else
    cp "$_QT/jar/QtAndroid.jar" "$_LIBS" || exit 1
fi
