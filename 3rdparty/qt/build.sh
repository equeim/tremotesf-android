#!/bin/bash

function _patch_if_needed() {
    # if can't reverse, patch
    if ! patch -p0 -R --dry-run -f -i $1; then
        patch -p0 -i $1 || ($2 && die)
    fi
}

_DIR="$(realpath $(dirname $0))"
cd "$_DIR" || exit 1

_patch_if_needed r18.patch false
_patch_if_needed qmakemake.patch false
_patch_if_needed logging.patch true
_patch_if_needed donottryondemand.patch true

_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR" || exit 1
cd "$_BUILD_DIR" || exit 1

_OPENSSL_LIBDIR="$(realpath ../../openssl/install-$ANDROID_ARCH/lib)"
_OPENSSL_INCDIR="$(realpath ../../openssl/install-$ANDROID_ARCH/include)"

_PREFIX="$(realpath ../install-$ANDROID_ARCH)"

OPENSSL_LIBS="-L$_OPENSSL_LIBDIR -lssl -lcrypto" ../qtbase/configure \
    -v \
    -confirm-license \
    -opensource \
    -prefix "$_PREFIX" \
    -xplatform android-clang \
    -c++std c++1z \
    -android-ndk "$ANDROID_NDK_ROOT" \
    -android-sdk "$ANDROID_SDK_ROOT" \
    -android-ndk-host linux-x86_64 \
    -android-arch "$ANDROID_ARCH" \
    -android-ndk-platform android-16 \
    -nomake tests \
    -nomake examples \
    -no-dbus \
    -no-gui \
    -no-opengl \
    -no-widgets \
    -no-feature-animation \
    -no-feature-bearermanagement \
    -no-feature-big_codecs \
    -no-feature-codecs \
    -no-feature-commandlineparser \
    -no-feature-datetimeparser \
    -no-feature-dnslookup \
    -no-feature-dom \
    -no-feature-filesystemiterator \
    -no-feature-filesystemwatcher \
    -no-feature-ftp \
    -no-feature-itemmodel \
    -no-feature-itemmodeltester \
    -no-feature-localserver \
    -no-feature-mimetype \
    -no-feature-networkdiskcache \
    -no-feature-networkproxy \
    -no-feature-process \
    -no-feature-processenvironment \
    -no-feature-sql \
    -no-feature-regularexpression \
    -no-feature-sharedmemory \
    -no-feature-statemachine \
    -no-feature-systemsemaphore \
    -no-feature-temporaryfile \
    -no-feature-testlib \
    -no-feature-translation \
    -no-feature-udpsocket \
    -no-feature-xml \
    -no-feature-xmlstream \
    -openssl-linked \
    -I"$_OPENSSL_INCDIR" || exit 1

make $MAKEOPTS || exit 1
make install $MAKEOPTS || exit 1

_LIBS="$_DIR/../../app/libs/$ANDROID_ARCH/"
mkdir -p "$_LIBS"
cp "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$ANDROID_ARCH/libc++_shared.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Core.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Concurrent.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Network.so" "$_LIBS"
