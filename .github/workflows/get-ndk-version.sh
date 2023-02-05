#!/bin/bash

# SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
#
# SPDX-License-Identifier: CC0-1.0

set -e -o pipefail
tomlq -r '.versions["sdk-ndk"]' gradle/libs.versions.toml
