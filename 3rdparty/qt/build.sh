#!/bin/bash

function _patch_if_needed() {
    # if can't reverse, patch
    echo
    echo "Applying patch $1"
    if patch -p1 -R --dry-run -f -i ../"$1" > /dev/null; then
        echo 'Already applied'
    else
        local _output="$(patch -p1 -i ../"$1")" _code=$?
        if [ $_code -ne 0 ]; then
            printf "%s\n" "$_output"
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

_DIR="$(realpath $(dirname $0))"
cd "$_DIR" || exit 1

cd qtbase

if [ ! -f dist/changes-5.12.0 ]; then
    echo "Minimum Qt version is 5.12.0, aborting"
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

_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR" || exit 1
cd "$_BUILD_DIR" || exit 1

_OPENSSL_LIBDIR="$(realpath ../../openssl/install-$ANDROID_ARCH/lib)"
_OPENSSL_INCDIR="$(realpath ../../openssl/install-$ANDROID_ARCH/include)"

_PREFIX="$(realpath ../install-$ANDROID_ARCH)"

_FLAGS=("-v"
    "-confirm-license"
    "-opensource"
    "-prefix $_PREFIX"
    "-xplatform android-clang"
    "-c++std c++1z"
    "-android-ndk $ANDROID_NDK_ROOT"
    "-android-sdk $ANDROID_SDK_ROOT"
    "-android-ndk-host linux-x86_64"
    "-android-arch $ANDROID_ARCH"
    "-android-ndk-platform android-$ANDROID_API"
    "-ltcg"
    "-nomake examples"
    "-nomake tests"
    "-no-dbus"
    "-no-gui"
    "-no-feature-animation"
    "-no-feature-bearermanagement"
    "-no-feature-big_codecs"
    "-no-feature-codecs"
    "-no-feature-commandlineparser"
    "-no-feature-datetimeparser"
    "-no-feature-dnslookup"
    "-no-feature-dom"
    "-no-feature-dtls"
    "-no-feature-filesystemiterator"
    "-no-feature-filesystemwatcher"
    "-no-feature-ftp"
    "-no-feature-itemmodel"
    "-no-feature-localserver"
    "-no-feature-mimetype"
    "-no-feature-networkdiskcache"
    "-no-feature-networkproxy"
    "-no-feature-process"
    "-no-feature-processenvironment"
    "-no-feature-settings"
    "-no-feature-sql"
    "-no-feature-sharedmemory"
    "-no-feature-statemachine"
    "-no-feature-systemsemaphore"
    "-no-feature-temporaryfile"
    "-no-feature-testlib"
    "-no-feature-textcodec"
    "-no-feature-translation"
    "-no-feature-udpsocket"
    "-no-feature-xml"
    "-no-feature-xmlstream"
    "-no-feature-regularexpression"
    "-openssl-linked"
    "-I$_OPENSSL_INCDIR"
)

OPENSSL_LIBS="-L$_OPENSSL_LIBDIR -lssl -lcrypto" ../qtbase/configure ${_FLAGS[@]} || exit 1

make $MAKEOPTS || exit 1
make install $MAKEOPTS || exit 1

_LIBS="$_DIR/../../app/libs/$ANDROID_ARCH/"
mkdir -p "$_LIBS"
cp "$_PREFIX/lib/libQt5Core.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Network.so" "$_LIBS"
