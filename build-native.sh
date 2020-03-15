#!/bin/bash

if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "ANDROID_SDK_ROOT is not set"
    exit 1
fi

if [ -z "$ANDROID_NDK_ROOT" ]; then
    echo "ANDROID_NDK_ROOT is not set"
    exit 1
fi

export TOP_DIR="$(realpath -- "$(dirname -- "$0")")"

export MAKEOPTS="$*"

export ANDROID_ABIS_32='armeabi-v7a x86'
export ANDROID_API_32=16

export ANDROID_ABIS_64='arm64-v8a x86_64'
export ANDROID_API_64=21

"$TOP_DIR/3rdparty/qt/build.sh" || exit 1
