# SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
#
# SPDX-License-Identifier: CC0-1.0

FROM opensuse/tumbleweed:latest

RUN zypper --non-interactive in --no-recommends findutils git gcc gcc-c++ make patch cmake ninja ccache 'pkgconfig(zlib)' 'cmake(double-conversion)' 'pkgconfig(libb2)' 'pkgconfig(libpcre2-posix)' \
    'cmake(Qt6HostInfo)' 'cmake(Qt6CoreTools)' 'cmake(Qt6WidgetsTools)' java-17-openjdk-devel unzip curl tar gzip zstd which perl 'perl(FindBin)' 'perl(File::Basename)' 'python3dist(yq)' jq
