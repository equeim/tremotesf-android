#!/bin/bash

readonly THIS_DIR="$(dirname -- "$(realpath -- "$0")")"
readonly OUT_DIR="$(realpath "$THIS_DIR/../java/org/equeim/libtremotesf")"

cd "$THIS_DIR" || exit 1
mkdir -p "$OUT_DIR" || exit 1
swig -c++ -java -package org.equeim.libtremotesf -outdir "$OUT_DIR" "$THIS_DIR/libtremotesf.i"
