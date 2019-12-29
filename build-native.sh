#!/bin/bash

export TOP_DIR="$(realpath -- "$(dirname -- "$0")")"

export MAKEOPTS="$*"

export ANDROID_ABIS_32='armeabi-v7a x86'
export ANDROID_API_32=16

export ANDROID_ABIS_64='arm64-v8a x86_64'
export ANDROID_API_64=21

"$TOP_DIR/3rdparty/openssl/build.sh" || exit 1
"$TOP_DIR/3rdparty/qt/build.sh" || exit 1
