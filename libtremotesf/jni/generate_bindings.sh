#!/bin/sh

JNI_DIR="$(realpath -- "$(dirname -- "$0")")"
cd "$JNI_DIR" || exit 1
mkdir -p java/org/equeim/libtremotesf
swig -c++ -java -package org.equeim.libtremotesf -outdir java/org/equeim/libtremotesf libtremotesf.i
