# Change Log

## [Unreleased]
### Added
- Show available free space when adding torrents
- Option to disable finished torrents notifications
- Option to notify about added torrents
- Options to notify about finished/added torrents since last connection to server
- Donation dialog is shown once 2 days after install

### Changed
- Tremotesf now uses the same C++ backend as the Desktop/Sailfish OS version

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
