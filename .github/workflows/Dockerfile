FROM fedora:latest

ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV SDKMANAGER /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager

COPY .github/workflows/get-ndk-version.sh gradle/libs.versions.toml /tmp/
RUN dnf -y --setopt=install_weak_deps=False install git gcc gcc-c++ make patch cmake ninja-build ccache 'pkgconfig(zlib)' 'cmake(double-conversion)' 'pkgconfig(libb2)' 'pkgconfig(libpcre2-posix)' \
    'cmake(Qt6HostInfo)' 'cmake(Qt6CoreTools)' 'cmake(Qt6WidgetsTools)' java-17-openjdk-devel unzip curl tar gzip zstd which perl 'perl(FindBin)' 'perl(File::Basename)' libtoml jq \
    && curl -o cmdline-tools.zip 'https://dl.google.com/android/repository/commandlinetools-linux-8092744_latest.zip' \
    && unzip cmdline-tools.zip \
    && rm cmdline-tools.zip \
    && mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" \
    && mv cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest" \
    && yes | "$SDKMANAGER" --licenses \
    && /tmp/get-ndk-version.sh /tmp/libs.versions.toml \
    && "$SDKMANAGER" "ndk;$(/tmp/get-ndk-version.sh /tmp/libs.versions.toml)" \
    && rm /tmp/get-ndk-version.sh /tmp/libs.versions.toml
