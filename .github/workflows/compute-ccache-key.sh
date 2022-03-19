#!/bin/bash

set -e -o pipefail

readonly FILES_HASH="$1"
if [[ -z $FILES_HASH ]]; then
    echo '::error ::Files hash argument is not specified'
    exit 1
fi

echo "Files hash = $FILES_HASH"

ccache_key="$FILES_HASH\n"

for git_dir in '3rdparty/openssl/openssl' '3rdparty/qt/qtbase'; do
    git_dir_commit="$(git -C "$git_dir" show -s --format='%H')"
    echo "Adding git commit $git_dir_commit for $git_dir"
    ccache_key+="$git_dir_commit\n"
done

compilers=(
    "$(which cc)"
    "$(which c++)"
)

if [[ -z $ANDROID_SDK_ROOT ]]; then
    echo '::error ::ANDROID_SDK_ROOT environment variable is either not set or empty'
    exit 1
fi

readonly latest_ndk="$(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d -print0 | LC_COLLATE=C.utf8 sort -nz | tail -z -n 1)"
if [[ -n $latest_ndk ]]; then
    echo "Latest NDK is ${latest_ndk}"
    readarray -d '' -O ${#compilers[@]} compilers < <(find "$latest_ndk" -xtype f -name clang -print0)
else
    echo '::warning ::Failed to determine latest NDK'
fi

echo "Compilers = ${compilers[@]}"
for compiler in "${compilers[@]}"; do
    compiler_realpath="$(realpath "$compiler")"
    compiler_hash="$(sha256sum "$compiler_realpath" | head -c 64)"
    echo "Adding hash $compiler_hash for $compiler_realpath"
    ccache_key+="$compiler_hash\n"
done

echo -e "\nCcache cache key before final hashing:\n$ccache_key"

ccache_key="$(echo -e "$ccache_key" | sha256sum | head -c 64)"

echo -e "Final ccache cache key: $ccache_key"

echo "CCACHE_CACHE_KEY=$ccache_key" >> "$GITHUB_ENV"
