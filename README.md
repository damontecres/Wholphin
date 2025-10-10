# Dolphin - an OSS Android TV client for Jellyfin

This is an Android TV client for [Jellyfin](https://jellyfin.org/). It aims to provide a Plex inspired UI experience for users migrating from Plex to Jellyfin.

This is not a fork of the [official client](https://github.com/jellyfin/jellyfin-androidtv). The user interface and controls have been written completely from scratch.

## Motivation

After using Plex and its Android TV app for years, I found the official Jellyfin Android TV client UI/UX to be a barrier to using Jellyfin more, so if you wish the interface and playback controls were a bit more like Plex's Android TV app, then Dolphin might work for you!

That said, Dolphin does not yet implement every feature in Jellyfin. It is a work in progress that will continue to improve over time.

## Distinguishing Features

- A navigation drawer for quick access to libraries, search, and settings from almost anywhere in the app
- Show Movie/TV Show titles when browsing libraries
- Play TV Show theme music, if available
- Plex inspired playback controls, such as:
  - Using D-Pad left/right for seeking during playback
  - Quickly access video chapters during playback
  - Optionally skip back a few seconds when resuming playback
- Other (subjective) enhancements:
  - Subtly show playback position along the bottom of the screen while seeking w/ D-Pad
  - Force Continue Watching & Next Up TV episodes to use their Series posters

## Installation

Downloader Code: `Dolphin CODE`

1. Enable side-loading "unknown" apps
    - https://androidtvnews.com/unknown-sources-chromecast-google-tv/
    - https://www.xda-developers.com/how-to-sideload-apps-android-tv/
    - https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources
    - https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/
1. Install the APK on your Android TV device with one of these options:
    - Install a browser program such as [Downloader](https://www.aftvnews.com/downloader/), use it to get the latest apk with short code `Dolphin ENTER A VALUE HERE` or URL: https://aftv.news/ Dolphin
    - Download the latest APK release from the [releases page](https://github.com/damontecres/Dolphin/releases/latest) or https://aftv.news/ Dolphin
        - Put the APK on an SD Card/USB stick/network share and use a file manager app from the Google Play Store / Amazon AppStore (e.g. `FX File Explorer`). Android's preinstalled file manager probably will not work!
        - Use `Send files to TV` from the Google Play Store on your phone & TV
        - (Expert) Use [ADB](https://developer.android.com/studio/command-line/adb) to install the APK from your computer ([guide](https://fossbytes.com/side-load-apps-android-tv/#h-how-to-sideload-apps-on-your-android-tv-using-adb))

### Upgrading the app

After the initial install above, the app will automatically check for updates. The updates can be installed in settings.

The first time you attempt an update, the OS should guide you through enabling the required additional permissions for the app to install updates.

## Compatibility

Requires Android 7.1+ (or Fire TV OS 6+) and Jellyfin server `10.10.x` (tested on primarily `10.10.7`).

The app is tested on a variety of Android TV/Fire TV OS devices, but if you encounter issues, please file an issue!

## Contributions

Issues and pull requests are always welcome! UI/UX improvements are especially desired!

Please check before submitting that your issue or pull request is not a duplicate.

If you plan to submit a pull request, please read the [contributing guide](CONTRIBUTING.md) before submitting!

## Acknowledgements

- Thanks to the Jellyfin team for creating and maintaining such a great open-source media server
- Thanks to the official Jellyfin Android TV client developers, some code for creating the device direct play profile is adapted from there
- Thanks to the Jellyfin Kotlin SDK developers for making it easier to interact with the Jellyfin server API
- Thanks to numerous other libraries that make app development even possible

## Additional screenshots

TODO
