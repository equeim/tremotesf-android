# Tremotesf
Android remote GUI for transmission-daemon.

<a href="https://f-droid.org/repository/browse/?fdid=org.equeim.tremotesf" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=org.equeim.tremotesf" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>

## Features
- View torrent list
- Sort torrents
- Filter torrents by name, status and trackers
- Start/stop/verify torrents with multi-selection
- Add torrents from torrent files and magnet links
- Select which files to download when adding torrent
- Manage torrent files
- Add and remove torrent trackers
- View torrent peers
- Set torrent limits
- Change remote server settings
- View server statistics
- Multiple servers
- Supports HTTPS connection
- Can connect to servers with self-signed certificates (you need to add certificate to server settings)
- Client certificate authentication

## Build
1. Download and install Android SDK. Download and install Android NDK (r16b or newer).
2. [Download](https://www.openssl.org/source) latest OpenSSL 1.1.x and unpack it to 3rdparty/openssl/openssl (removing version from directory name).
3. [Download](http://download.qt.io/official_releases/qt/5.11/5.11.1/submodules/qtbase-everywhere-src-5.11.1.tar.xz) Qt base submodule from 5.11 branch and unpack it to 3rdparty/qt/qtbase.
5. Set ANDROID_SDK_ROOT environment variable to the root directory of Android SDK and ANDROID_NDK_ROOT environment variable to the root directory of Android NDK.
6. Execute build-native.sh script from project root directory.
7. Build APK in Android Studio.

## Translations
Translations are managed on [Transifex](https://www.transifex.com/equeim/tremotesf-android).

## Donate
I you like this app, you can support its development via [PayPal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=DDQTRHTY5YV2G&item_name=Support%20Tremotesf%20(Android)%20development&no_note=2&item_number=1&no_shipping=1&currency_code=USD) or [Yandex.Money](https://yasobe.ru/na/equeim_tremotesf_android).

## Screenshots
![](http://i.imgur.com/tsmKQIV.png) ![](http://i.imgur.com/HMucsni.png) ![](http://i.imgur.com/EXc9CG0.png)
