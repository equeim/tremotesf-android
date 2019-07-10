#!/bin/bash

function _patch_if_needed() {
    # if can't reverse, patch
    if ! patch -p1 -R --dry-run -f -i ../$1; then
        if (! patch -p1 -i ../$1) && [ $2 = true ]; then
            exit 1
        fi
    fi
}

function _trim() {
    awk '{$1=$1};1'
}

_DIR="$(realpath $(dirname $0))"
cd "$_DIR" || exit 1

cd qtbase

if [ ! -f dist/changes-5.11.3 ]; then
    echo "Minimum Qt version is 5.11.3, aborting"
    exit 1
fi

_patch_if_needed qmakemake.patch false
_patch_if_needed donottryondemand.patch true

_QT_VERSION=$(grep MODULE_VERSION .qmake.conf | cut -d= -f2 | _trim)
_QT_VERSION_MINOR=$(echo $_QT_VERSION | cut -d'.' -f2)
_QT_VERSION_PATCH=$(echo $_QT_VERSION | cut -d'.' -f3)
_NDK_REVISION=$(grep Pkg.Revision "$ANDROID_NDK_ROOT/source.properties" | cut -d= -f2 | _trim | cut -d'.' -f1)

case "$_QT_VERSION_MINOR" in
    11)
        # fix for GCC 9 host compiler
        _patch_if_needed 5.11_qrandom_gcc9.patch true

        # fix for NDK r18 and newer, works with r16 and r17 too
        _patch_if_needed 5.11_ndk-r18.patch true

        if [ $_NDK_REVISION -ge 20 ]; then
            # fix for NDK r20 and newer
            _patch_if_needed 5.11_ndk-r20.patch true
        fi
    ;;
    12)
        if [ "$_QT_VERSION_PATCH" -le 4 -a "$_NDK_REVISION" -ge 20 ]; then
            # fix for NDK r20 and newer
            _patch_if_needed 5.12_ndk-r20.patch true
        fi
    ;;
    13)
        if [ "$_QT_VERSION_PATCH" -eq 0 -a "$_NDK_REVISION" -ge 20 ]; then
            # fix for NDK r20 and newer
            _patch_if_needed 5.12_ndk-r20.patch true
        fi
    ;;
esac

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

if [ $_QT_VERSION_MINOR -ge 12 ]; then
    _FLAGS+=("-no-feature-dtls")
fi

OPENSSL_LIBS="-L$_OPENSSL_LIBDIR -lssl -lcrypto" ../qtbase/configure ${_FLAGS[@]} || exit 1

make $MAKEOPTS || exit 1
make install $MAKEOPTS || exit 1

_LIBS="$_DIR/../../app/libs/$ANDROID_ARCH/"
mkdir -p "$_LIBS"
cp "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$ANDROID_ARCH/libc++_shared.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Core.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Concurrent.so" "$_LIBS"
cp "$_PREFIX/lib/libQt5Network.so" "$_LIBS"
