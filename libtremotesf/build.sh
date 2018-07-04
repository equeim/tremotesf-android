#!/bin/sh

_DIR="$(realpath $(dirname $0))"
_BUILD_DIR="$_DIR/build-$ANDROID_ARCH"
mkdir -p "$_BUILD_DIR"
cd "$_BUILD_DIR"
_QT="$(realpath $_DIR/../3rdparty/qt/install-$ANDROID_ARCH)"
"$_QT/bin/qmake" CONFIG+=release ../libtremotesf.pro
make $MAKEOPTS
make INSTALL_ROOT="$_DIR/../app" install
