#!/bin/bash

set -e -o pipefail
tomlq -r '.versions["sdk-ndk"]' gradle/libs.versions.toml
