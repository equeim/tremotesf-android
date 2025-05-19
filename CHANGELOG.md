# Changelog

## [2.12.0] - 2025-05-19
### Added
- Labels support (thanks @sommd for initial implementation)
- Android 15 support

### Changed
- Certificates installed by the user in Android system settings can now be used by the app

### Fixed
- Merging of trackers with old Transmission versions
- Saving of torrent adding parameters

## [2.11.1] - 2024-06-19
### Fixed
- Error when merging trackers with Transmission older than 4.0
- Custom connection timeout not working properly
- Saving last used download directory when adding torrent

## [2.11.0] - 2024-05-08
### Added
- Merging trackers when adding already existing torrent

### Changed
- Custom TLS certificates can now be configured without enabling HTTPS, to support HTTP to HTTPS redirection
- Limit on size of torrent files is increased to 20 MiB

### Fixed
- Notifications not being shown when app is in foreground and on main screen, or when it is in background and persistent notification is active
- Changing alternative upload speed limit
- State of selection toolbar in the list of torrent's files
- Start / Start now actions being inverted

### Removed
- Support of Android versions prior to 8.0

## [2.10.2] - 2024-03-20
### Changed
- Updated translations

### Fixed
- Incorrect file names in torrent's file list

## [2.10.1] - 2024-01-16
### Fixed
- Connection settings being wiped on upgrade

## [2.10.0] - 2024-01-13
### Added
- Android 14 support
- Predictive back gesture support

### Changed
- RPC client is rewritten to use OkHttp library and native Android network stack instead of Qt
- All torrent adding parameters are now remembered together with download directory
- Notifications badge in applications launcher is now not shown for persistent notification
  (only for new installations, existing users can disable it in system notification settings)

## [2.9.1] - 2023-03-26
### Fixed
- Invalid display of download directory filter
- Zero leechers count
- Crashes

## [2.9.0] - 2023-03-20
### Added
- Number of seeders and leechers is shown per tracker

### Changed
- Vcpkg is used to build C++ dependencies (it is downloaded and set up automatically by Gradle task)
- NDK is updated to r25c (25.2.9519653)
- Notification permission request is not shown again if it was dismissed or permission was denied
- When torrents list sort settings are changed it's scrolled to top

### Fixed
- Rare crash when opening app
- Crashes when opening certain broken torrent files
- Infinite connection indicator when connection fails

## [2.8.0] - 2023-02-06
### Added
- Torrent link can be automatically filled from clipboard when adding torrent (disabled by default)
- Drag & drop support for adding torrents
- More efficient data transfer with Transmission server is used when Transmission version is at least 3.00
- When adding torrent last download directory is now remembered (can be disabled in settings)
- Information about web seeders is displayed on torrents' details screen

### Changed
- When relative time displayed for torrent's added/created/last active dates, absolute date and time is also shown
- "Seeders" and "leechers" now refer to total number of seeders and leechers reported by trackers,
  while number of peers that we are currently downloading from / uploading to is displayed separately

### Fixed
- Fixed display of trackers' next update time
- File paths are now displayed with correct separators when Transmission is running on Windows
- Fix position of bottom messages when 3 button navigation is enabled

## [2.7.2] - 2022-11-12
### Fixed
- Crashes when snackbar is shown

## [2.7.1] - 2022-11-09
### Fixed
- Building app on Debian

## [2.7.0] - 2022-11-04
### Added
- Android 13 support
- Themed icons support
- Per-app language preferences support

### Fixed
- Keyboard now doesn't overlap app's UI elements
- Fixed crashes
- IPv6 address can now be used to connect to server

## [2.6.1] - 2022-05-10
### Added
- Detailed connection error message can now be seen from main screen
  (most useful when figuring out TLS issues)

### Changed
- Colors for new Material You theme are changed to be more like old style,
  primary color is not used to signal surface elevation
- Updated OpenSSL to 3.0.3 and Qt to 6.3.0

### Fixed
- Fixed showing of connection error toast when returning to application

## [2.6.0] - 2022-04-28
### Added
- System colors support on Android 12
- Disconnect button is shown on bottom toolbar when connecting to server

### Changed
- Theme updated for Material You design
- Updated OpenSSL to 3.0.2 and Qt to 6.2.4

## [2.5.4] - 2021-12-04
### Fixed
- Fixed crashes when removing torrents

## [2.5.3] - 2021-12-02
### Changed
- Updated Qt to 6.2.2

### Fixed
- Fixed F-Droid build

## [2.5.2] - 2021-11-23
### Changed
- Improved compatibility with Android 12

### Fixed
- Fixed compatibility with Let's Encrypt ceritificates on Android < 7.1.1
- Fixed saving of settings in server edit screen
- Fixed automatic connection to server based on Wi-Fi SSID when VPN is active

## [2.5.1] - 2021-10-03
### Changed
- Removed ACCESS_BACKGROUND_LOCATION from google flavor

## [2.5.0] - 2021-09-20
### Added
- Fast scroll support in torrents list
- PEM certificate can now be loaded from a file
- Swipe to refresh feature
- Quick scroll to top by tapping on top toolbar on main screen, files list and peers list
- Server stats dialog now shows free space in download directory

### Changed
- Optimized opening of torrent files with large number of files
- Migrated to Qt 6
- Redesigned main screen

### Removed
- Dropped support of Android 4.x
- Google Play donations

## [2.4.2] - 2021-05-21
### Fixed
- Fixed crash with single-file torrents

## [2.4.1] - 2021-05-21
### Fixed
- Fixed selection of files and their priorities when adding torrents
- Fixed files wrongly displayed as selected when they are not

## [2.4.0] - 2021-05-02
### Added
- Automatic connection to servers when connecting to Wi-Fi networks
- New menu item for torrents to force start them ("Start now" feature)

### Changed
- Disabled APK ABI splitting
- OpenSSL and Qt are now set up as Git submodules
- Use KDE's Qt 5.15 branch
- NDK requirements: NDK r22b and CMake 3.18

### Fixed
- Fixed opening file:// URIs on Android 10
- Fixed setting alternative speed limits
- Memory leaks

## [2.3.2] - 2021-02-06
### Fixed
- Fixed another crash when adding torrent link
- Fixed crash when renaming torrents

## [2.3.1] - 2021-02-06
### Fixed
- Crash when adding torrent link

## [2.3.0] - 2021-01-31
### Changed
- Improved performance and memory usage when opening torrent files

### Fixed
- Fixed crashes when changing torrent filters
- Fixed download directory filter not showing some torrents
- Fixed renaming of torrent files

## [2.2.0] - 2020-10-25
### Added
- Added support of Qt 5.15
- Added support of sharing torrents' magnet links

### Changed
- Added support of drawing under status bar and navigation bar

### Fixed
- Fixed displaying multiple trackers for torrent
- Fixed displaying speed limits in kB/s

## [2.1.1] - 2020-04-01
### Fixed
- Fixed crash when torrent metadata is downloaded while torrent's files tab is active
- Fixed memory leak in torrent's files and peers tabs

## [2.1.0] - 2020-03-29
### Added
- Added support of renaming torrent's files when adding it
- Added support of configuring per-server HTTP/SOCKS5 proxies
- Added ability to add multiple trackers at a time
- Added support of Qt 5.14

### Changed
- Dropped support of ARMv7 devices without NEON instructions

### Fixed
- Fixed swapped high/low file priorities when adding torrent
- Fixed confirm button navigating to parent directory when adding torrent
- Fixed some crashes

## [2.0.2] - 2020-01-11
### Fixed
- Fixed Google Play donations

## [2.0.1] - 2020-01-01
### Fixed
- Fixed VIBRATE permission

## [2.0.0] - 2020-01-01
### Added
- Added support of Android 10 dark theme

### Changed
- Switching between dark/light themes now doesn't require app restart
- Switching between old/new colors now doesn't require app restart
- Updated Material design theme
- Migrated to single activity architecture

### Fixed
- Fixed deleted items in download directory text field's menu coming back after rotating device
- Fixed notifications crash on Android 4.1 and 4.2.0
- Fixed renaming torrent's files

## [1.10.1] - 2019-10-29
### Fixed
- Fixed crash when adding new tracker
- Fixed restoring selected torrents after device rotation
- Fixed restoring current directory after device rotation in torrent's files view

## [1.10.0] - 2019-10-26
### Added
- Added support of Android 10

### Changed
- Enabling persistent notification now prevents app from switching to background update mode
- Minor performance improvements

### Removed
- Removed per-server background update intervals

### Fixed
- Fixed background update on newer Android versions

## [1.9.3] - 2019-08-20
### Fixed
- Fixed crash when torrent's name contains number larger than 32-bit integer

## [1.9.2] - 2019-08-18
### Changed
- Raised minimum NDK version to r19
- Raised minimum Qt version to 5.12
- libtremotesf is now built using CMake, which also adds syntax highlighting and autocompletion in Android Studio
- Enabled LTO for native libraries

### Fixed
- Fixed freeze when connecting to server with large number of torrents

## [1.9.1] - 2019-08-01
### Added
- 64-bit support for native libraries
- NDK r20 support

### Changed
- Minor performance improvements
- Updated translations

### Fixed
- Fixed light navigation bar with dark theme on some Android devices
- Fixed crash when torrent is using 'Seed regardless of activity' mode
- Fixed updating torrent's file list when inside directory

## [1.9.0] - 2019-02-09
### Added
- Tremotesf now remebmers used download directories and shows them in dropdown menu when adding torrent / changing location
- Added option to enable compact view for torrents
- Added option to show torrents' names on multiple lines
- Added ability to rename torrent directly from its context menu or its properties screen's menu

### Changed
- Updated translations

### Fixed
- Fixed resetting of servers spinner's current item after orientation change
- Fixed updating of  servers spinner's current item after changing current server in ServersActivity
- Fixed Ratio Limit Mode and Idle Seeding Mode spinners with zh_CN translation
- Fixed toggling alternative speed limits from MainActivity menu

## [1.8.5] - 2018-12-22
### Changed
- Updated Qt to version 5.12.0
- Updated OpenSSL to version 1.1.1a
- C++ backend's log entries now has "LibTremotesf" tag
- Updated translations

### Fixed
- Fixed setting files' download state by ticking checkbox
- Fixed setting files' priority

## [1.8.4] - 2018-09-19
### Changed
- Connection error messages are now displayed in a toast

### Fixed
- Fixed loading of system CA certificates
- Fixed action bar height in landscape mode
- Added tag to log entries

## [1.8.3] - 2018-09-11
### Changed
- OpenSSL 1.1.1 is required
- OpenSSL is now built using Clang

## [1.8.2] - 2018-09-02
### Changed
- Migrated from com.android.support support library to androidx
- Migrated from Theme.Design theme to Theme.MaterialComponents
- Tremotesf now uses red as accent color (you can revert it back in settings)

### Fixed
- Fixed crash when disconnecting from server

## [1.8.1] - 2018-08-16
### Fixed
- Fixed crash when disconnecting from server
- "Remove" menu item in torrent's properties activity is now hidden when disconnecting/torrent removal

## [1.8.0] - 2018-08-10
### Added
- Added ability to reannounce torrents
- Added support of Android 9

### Changed
- Added ability set location of multiple torrents at once
- Updated translations

### Fixed
- Crash on saving servers
- Improved support of self-signed certificates
- All network requests are now aborted when disconnecting from server

## [1.7.1] - 2018-07-04
### Changed
- Qt updated to version 5.11.1
- Switched to OpenSSL 1.1
- Native libraries are now build using latest NDK
- Qt and libtremotesf are now build using Clang
- Updated translations

### Fixed
- Not disconnecting when removing last server
- Servers spinner when adding first server / removing last server
- Fixed notifications when connecting to server after disconnect

## [1.7.0] - 2018-06-11
### Added
- Show available free space when adding torrents
- Option to disable finished torrents notifications
- Option to notify about added torrents
- Options to notify about finished/added torrents since last connection to server
- Donation dialog is shown once 2 days after install

### Changed
- Tremotesf now uses the same C++ backend as the Desktop/Sailfish OS version
- Minimum Android version raised to 4.1

### Fixed
- Yandex.Money donate link

## [1.6.3] - 2018-05-09
### Fixed
- Crash when adding torrent file
- Crashes in some weird cases

## [1.6.2] - 2018-05-04
### Changed
- Updated translations

### Fixed
- Crashes with some uncompleted translations
- Crashes on Android versions older than 6.0
- License tab on About screen

## [1.6.1] - 2018-04-30
### Fixed
- Crash when restoring activity with no servers configured

## [1.6.0] - 2018-04-30
### Added
- Support of Android 8.1
- Support of adaptive icons
- Filter torrents by download directory (thanks to beschoenen)
- Donations support (Google Play, PayPal and Yandex.Money)

### Fixed
- Various crashes

## [1.5.1] - 2017-04-13
### Fixed
- Crashes in torrent's files view

## [1.5.0] - 2017-04-09
### Added
- Option to delete torrent's files from hard disk by default when removing torrent
- Menu item for quick enabling/disabling alternative speed limits

### Changed
- Torrents status and tracker filters are restored after app restart and reconnection
- Torrents sort mode and status filter are not reset after disconnecting

### Fixed
- Torrent properties activity doesn't release from memory until files tree is created (memory leak when torrent has large number of files)

## [1.4.2] - 2017-03-07
### Changed
- Trim whitespaces from fields in ServerEditActivity before saving server
- More correct hint text for server's address field

### Fixed
- Correctly check server URL
- Notification actions icons are black on Lollipop+ (for ROMs that doesn't automatically recolor them, e.g. MIUI)
- Show "Disconnect" action on notification when connectiong to server

## [1.4.1] - 2017-02-25
### Changed
- License is now shown in WebView
- Translations are now managed on Transifex.

## [1.4.0] - 2017-02-17
### Added
- Show authentication error
- "About" activity

## [1.3.0] - 2017-02-10
### Added
- Fast scroll for torrent's files

### Changed
- Torrents with large number of files now load a lot faster.

## [1.2.1] - 2017-02-04
### Changed
- Switched to another Bencode library (slightly faster)

## [1.2.0] - 2017-02-03
### Added
- Changing torrents sort order
- Added date is now shown on torrent's details tab

### Changed
- Torrents sort mode is saved in settings

### Fixed
- Torrent status icon with light theme
- Sort by added date now works

## [1.1.0] - 2017-02-01
### Added
- Set torrent location
- Rename torrent's files

### Fixed
- Crash if tracker's address is an IP address

## [1.0.1] - 2017-01-31
### Fixed
- Progress bar visibility on Add torrent file activity
- More correct starting Add torrents activities from other applications
- Fixed one string in Russian translation

## [1.0.0] - 2017-01-29
### Added
- Everything.
