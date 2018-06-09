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
Download and install Android SDK. Download NDK r15c (OpenSSL does not build with newer versions) from [there](https://developer.android.com/ndk/downloads/older_releases).

Download and install Qt SDK, make sure to select "Android x86" and "Android ARMv7" components.

Navigate to tremotesf-android root (where build-native.sh is located).

Set ANDROID_NDK_ROOT environment variable to the root directory of Android NDK bundle, and QT_ROOT to the root directory of selected Qt version (where android_armv7 and android_x86 directories are located, i.e. ~/Qt/5.11.0).

Execute build-native.sh script from project root directory.

Build APK in Android Studio.

## Translations
Translations are managed on [Transifex](https://www.transifex.com/equeim/tremotesf-android).

## Donate
I you like this app, you can support its development via [PayPal](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=DDQTRHTY5YV2G&item_name=Support%20Tremotesf%20(Android)%20development&no_note=2&item_number=1&no_shipping=1&currency_code=USD) or [Yandex.Money](https://yasobe.ru/na/equeim_tremotesf_android).

## Screenshots
![](http://i.imgur.com/tsmKQIV.png) ![](http://i.imgur.com/HMucsni.png) ![](http://i.imgur.com/EXc9CG0.png)
