#!/bin/bash

readonly QT_DIR="$(realpath -- "$(dirname -- "$0")")"

function patch_if_needed() {
    # if can't reverse, patch
    local -r patch="$1"
    local -r fail_on_failure="$2"
    echo
    echo "Applying patch $patch"
    if patch -p1 -R --dry-run -f -i "../$patch" > /dev/null; then
        echo 'Already applied'
    else
        local -r output="$(patch -p1 -i "../$patch")" code="$?"
        if [ "$code" -ne 0 ]; then
            printf '%s\n' "$output"
            if [ "$fail_on_failure" = true ]; then
                echo 'Failed to apply patch, exiting'
                exit 1
            fi
            echo 'Failed to apply patch, continuing'
        else
            echo 'Applied'
        fi
    fi
}

function build() {
    local -r abi="$1"
    local -r api="$2"

    echo "Building Qt for $abi API $api"

    cd "$QT_DIR/qtbase" || return 1

    if [ ! -f dist/changes-5.12.0 ]; then
        echo 'Minimum Qt version is 5.12.0, aborting'
        return 1
    fi

    patch_if_needed qmakemake.patch false
    patch_if_needed java7.patch false
    patch_if_needed donottryondemand.patch true
    patch_if_needed o2.patch true

if [ ! -f dist/changes-5.14.0 ]; then
    # NDK r19 toolchain
    patch_if_needed 067664531853a1e857c777c1cc56fc64b272e021.patch false
    patch_if_needed mips.patch false
    patch_if_needed ndk-r19.patch true
fi

    # LTO
    patch_if_needed thin-lto.patch true
    patch_if_needed ltcg-armv7.patch true
    patch_if_needed openssl-test-ltcg.patch true

    local -r build_dir="$QT_DIR/build-$abi"
    mkdir -p "$build_dir" || return 1
    cd "$build_dir" || return 1

    local -r openssl_libdir="$QT_DIR/../openssl/install-$abi/lib"
    local -r openssl_incdir="$QT_DIR/../openssl/install-$abi/include"

    local -r prefix="$QT_DIR/install-$abi"

    local -r flags=(
        '-v'
        '-confirm-license'
        '-opensource'
        '-prefix' "$prefix"
        '-xplatform' 'android-clang'
        '-c++std' 'c++1z'
        '-android-ndk' "$ANDROID_NDK_ROOT"
        '-android-sdk' "$ANDROID_SDK_ROOT"
        '-android-ndk-host' 'linux-x86_64'
        '-android-arch' "$abi"
        '-android-ndk-platform' "android-$api"
        '-ltcg'
        '-nomake' 'examples'
        '-nomake' 'tests'
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

    export OPENSSL_LIBS="-L$openssl_libdir -lssl -lcrypto"
    "$QT_DIR/qtbase/configure" "${flags[@]}" || return 1

    make $MAKEOPTS || return 1
    make install $MAKEOPTS || return 1

    local -r libs="$TOP_DIR/app/libs/$abi/"
    mkdir -p "$libs" || return 1
    cp "$prefix/lib/libQt5Core.so" "$libs" || return 1
    cp "$prefix/lib/libQt5Network.so" "$libs" || return 1

    echo
}

for abi in $ANDROID_ABIS_32; do
    build "$abi" "$ANDROID_API_32" || exit 1
done

for abi in $ANDROID_ABIS_64; do
    build "$abi" "$ANDROID_API_64" || exit 1
done

cp "$QT_DIR/install-armeabi-v7a/jar/QtAndroid.jar" "$TOP_DIR/app/libs/" || exit 1
