#!/bin/sh

NPROC="$(nproc)"

mkdir -p build-arm
cd build-arm
QT="$QT_ROOT/android_armv7"
"$QT/bin/qmake" CONFIG+=jni ../libtremotesf.pro
make -j"$NPROC"
make INSTALL_ROOT="../../app" install
LIBS="../../app/libs/armeabi-v7a/"
cp "$QT/lib/libQt5Core.so" "$LIBS"
cp "$QT/lib/libQt5Concurrent.so" "$LIBS"
cp "$QT/lib/libQt5Network.so" "$LIBS"
cp "$ANDROID_NDK_ROOT/sources/cxx-stl/gnu-libstdc++/4.9/libs/armeabi-v7a/libgnustl_shared.so" "$LIBS"
cd -

mkdir -p build-x86
cd build-x86
QT="$QT_ROOT/android_x86"
"$QT/bin/qmake" CONFIG+=jni ../libtremotesf.pro
make -j"$NPROC"
make INSTALL_ROOT="../../app" install
LIBS="../../app/libs/x86/"
cp "$QT/lib/libQt5Core.so" "$LIBS"
cp "$QT/lib/libQt5Concurrent.so" "$LIBS"
cp "$QT/lib/libQt5Network.so" "$LIBS"
cp "$ANDROID_NDK_ROOT/sources/cxx-stl/gnu-libstdc++/4.9/libs/x86/libgnustl_shared.so" "$LIBS"
cd -

if [ -f "$QT/jar/QtAndroid-bundle.jar" ]; then
    cp "$QT/jar/QtAndroid-bundle.jar" "../app/libs/"
else
    cp "$QT/jar/QtAndroid.jar" "../app/libs/"
fi
