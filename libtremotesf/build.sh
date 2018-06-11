#!/bin/sh

_DIR="$(realpath $(dirname $0))"
_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR"
cd "$_BUILD_DIR"
_QT="$(realpath $_DIR/../3rdparty/qt/install-$ANDROID_ARCH)"
"$_QT/bin/qmake" CONFIG+=jni ../libtremotesf.pro
make $MAKEOPTS
make INSTALL_ROOT="$_DIR/../app" install
_LIBS="$_DIR/../app/libs/$ANDROID_ARCH/"
cp "$ANDROID_NDK_ROOT/sources/cxx-stl/gnu-libstdc++/4.9/libs/$ANDROID_ARCH/libgnustl_shared.so" "$_LIBS"
cp "$_QT/lib/libQt5Core.so" "$_LIBS"
cp "$_QT/lib/libQt5Concurrent.so" "$_LIBS"
cp "$_QT/lib/libQt5Network.so" "$_LIBS"
