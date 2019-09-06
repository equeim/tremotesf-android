#!/bin/bash

function _build() {
    "$top_dir/3rdparty/openssl/build.sh" || return 1
    "$top_dir/3rdparty/qt/build.sh" || return 1
    return 0
}

readonly top_dir=$(realpath $(dirname "$0"))

export MAKEOPTS="$@"

export ANDROID_API=16

export ANDROID_ARCH=armeabi-v7a
_build || exit 1

export ANDROID_ARCH=x86
_build || exit 1

export ANDROID_API=21

export ANDROID_ARCH=arm64-v8a
_build || exit 1

export ANDROID_ARCH=x86_64
_build || exit 1

readonly qt=$(realpath "$top_dir/3rdparty/qt/install-armeabi-v7a")
cp "$qt/jar/QtAndroid.jar" "$top_dir/app/libs/" || exit 1
