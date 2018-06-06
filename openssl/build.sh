#!/bin/bash

NPROC="$(nproc)"

make clean

export _ANDROID_EABI="arm-linux-androideabi-4.9"
export _ANDROID_ARCH="arch-arm"
source setenv-android.sh
./config shared no-ssl2 no-ssl3 no-comp no-hw no-engine
make depend -j"$NPROC"
make all -j"$NPROC"
LIBS="../app/libs/armeabi-v7a/"
cp -L libcrypto.so "$LIBS"
cp -L libssl.so "$LIBS"

make clean

export _ANDROID_EABI="x86-4.9"
export _ANDROID_ARCH="arch-x86"
source setenv-android.sh
./config shared no-ssl2 no-ssl3 no-comp no-hw no-engine
make depend -j"$NPROC"
make all -j"$NPROC"
LIBS="../app/libs/x86/"
cp -L libcrypto.so "$LIBS"
cp -L libssl.so "$LIBS"
