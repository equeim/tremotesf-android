#!/bin/bash

function _patch_if_needed() {
    # if can't reverse, patch
    echo
    echo "Applying patch $1"
    if patch -p1 -R --dry-run -f -i "../$1" > /dev/null; then
        echo 'Already applied'
    else
        local output=$(patch -p1 -i "../$1") code="$?"
        if [ "$code" -ne 0 ]; then
            printf '%s\n' "$output"
            if [ "$2" = true ]; then
                echo 'Failed to apply patch, exiting'
                exit 1
            fi
            echo 'Failed to apply patch, continuing'
        fi
    fi
}

function _trim() {
    awk '{$1=$1};1'
}

echo
echo "Building Qt for $ANDROID_ARCH"

readonly top_dir=$(realpath $(dirname "$0"))
cd "$top_dir" || exit 1

cd qtbase

if [ ! -f dist/changes-5.12.0 ]; then
    echo 'Minimum Qt version is 5.12.0, aborting'
    exit 1
fi

_patch_if_needed qmakemake.patch false
_patch_if_needed java7.patch false
_patch_if_needed donottryondemand.patch true
_patch_if_needed o2.patch true

# NDK r19 toolchain
_patch_if_needed 067664531853a1e857c777c1cc56fc64b272e021.patch false
_patch_if_needed mips.patch false
_patch_if_needed ndk-r19.patch true

# LTO
_patch_if_needed thin-lto.patch true
_patch_if_needed ltcg-armv7.patch true
_patch_if_needed openssl-test-ltcg.patch true

cd -

readonly build_dir="$top_dir/build-$ANDROID_ARCH"
mkdir -p "$build_dir" || exit 1
cd "$build_dir" || exit 1

readonly openssl_libdir=$(realpath "../../openssl/install-$ANDROID_ARCH/lib")
readonly openssl_incdir=$(realpath "../../openssl/install-$ANDROID_ARCH/include")

readonly prefix=$(realpath "../install-$ANDROID_ARCH")

_FLAGS=('-v'
    '-confirm-license'
    '-opensource'
    "-prefix $prefix"
    '-xplatform android-clang'
    '-c++std c++1z'
    "-android-ndk $ANDROID_NDK_ROOT"
    "-android-sdk $ANDROID_SDK_ROOT"
    '-android-ndk-host linux-x86_64'
    "-android-arch $ANDROID_ARCH"
    "-android-ndk-platform android-$ANDROID_API"
    '-ltcg'
    '-nomake examples'
    '-nomake tests'
    '-no-dbus'
    '-no-gui'
    '-no-feature-animation'
    '-no-feature-bearermanagement'
    '-no-feature-big_codecs'
    '-no-feature-codecs'
    '-no-feature-commandlineparser'
    '-no-feature-datetimeparser'
    '-no-feature-dnslookup'
    '-no-feature-dom'
    '-no-feature-dtls'
    '-no-feature-filesystemiterator'
    '-no-feature-filesystemwatcher'
    '-no-feature-ftp'
    '-no-feature-itemmodel'
    '-no-feature-localserver'
    '-no-feature-mimetype'
    '-no-feature-networkdiskcache'
    '-no-feature-networkproxy'
    '-no-feature-process'
    '-no-feature-processenvironment'
    '-no-feature-settings'
    '-no-feature-sql'
    '-no-feature-sharedmemory'
    '-no-feature-statemachine'
    '-no-feature-systemsemaphore'
    '-no-feature-temporaryfile'
    '-no-feature-testlib'
    '-no-feature-textcodec'
    '-no-feature-translation'
    '-no-feature-udpsocket'
    '-no-feature-xml'
    '-no-feature-xmlstream'
    '-no-feature-regularexpression'
    '-openssl-linked'
    "-I$openssl_incdir"
)

OPENSSL_LIBS="-L$openssl_libdir -lssl -lcrypto" ../qtbase/configure ${_FLAGS[@]} || exit 1

make $MAKEOPTS || exit 1
make install $MAKEOPTS || exit 1

readonly libs="$top_dir/../../app/libs/$ANDROID_ARCH/"
mkdir -p "$libs"
cp "$prefix/lib/libQt5Core.so" "$libs"
cp "$prefix/lib/libQt5Network.so" "$libs"
