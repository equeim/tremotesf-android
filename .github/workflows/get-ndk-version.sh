#!/bin/bash

toml -t gradle/libs.versions.toml -g versions.ndk -j | jq -r .ndk.value
