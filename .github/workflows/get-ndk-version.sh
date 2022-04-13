#!/bin/bash

set -e -o pipefail
toml -t gradle/libs.versions.toml -g 'versions.sdk-ndk' -j | jq -r '.["sdk-ndk"].value'
