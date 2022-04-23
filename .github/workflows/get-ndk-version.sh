#!/bin/bash

set -e -o pipefail
versions="$1"
if [[ -z $versions ]]; then
    versions='gradle/libs.versions.toml'
fi
toml -t "$versions" -g 'versions.sdk-ndk' -j | jq -r '.["sdk-ndk"].value'
