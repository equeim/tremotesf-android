#!/bin/bash

# SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
#
# SPDX-License-Identifier: CC0-1.0

readonly THIS_DIR="$(dirname -- "$(realpath -- "$0")")"
readonly OUT_DIR="$(realpath "$THIS_DIR/../java/org/equeim/libtremotesf")"

cd "$THIS_DIR" || exit 1
mkdir -p "$OUT_DIR" || exit 1
swig -v -c++ -java -package org.equeim.libtremotesf -outdir "$OUT_DIR" "$THIS_DIR/libtremotesf.i"
