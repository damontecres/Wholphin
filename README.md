# Wholphin - an OSS Android TV client for Jellyfin

> "Never half-phin two jellies. Always wholphin one jelly."

Wholphin is an open-source Android TV client for Jellyfin. It aims to provide a different app UI that's inspired by Plex for users interested in migrating to Jellyfin.

This is not a fork of the [official client](https://github.com/jellyfin/jellyfin-androidtv). Wholphin's user interface and controls have been written completely from scratch. Wholphin uses the same media player (media3/ExoPlayer) as the official client.

<img width="1280" height="771" alt="0 1 0_01_home" src="https://github.com/user-attachments/assets/599d7f06-d3b5-4553-b37f-19a9726f058f" />

## Features

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
- Different media playback engines, including:
  - Default ExoPlayer/Media3
  - Experimental MPV

### Roadmap

See [here for the roadmap](https://github.com/damontecres/Wholphin/wiki#roadmap)

## Installation

Downloader Code: `8668671`

1. Enable side-loading "unknown" apps
    - https://androidtvnews.com/unknown-sources-chromecast-google-tv/
    - https://www.xda-developers.com/how-to-sideload-apps-android-tv/
    - https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources
    - https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/
1. Install the APK on your Android TV device with one of these options:
    - Install a browser program such as [Downloader](https://www.aftvnews.com/downloader/), use it to get the latest apk with short code `8668671` or URL: http://aftv.news/8668671
    - Download the latest APK release from the [releases page](https://github.com/damontecres/Wholphin/releases/latest) or http://aftv.news/8668671
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

Issues and pull requests are always welcome! Please check before submitting that your issue or pull request is not a duplicate.

If you plan to contribute, please read the [contributing guide](CONTRIBUTING.md)!

## Acknowledgements

- Thanks to the Jellyfin team for creating and maintaining such a great open-source media server
- Thanks to the official Jellyfin Android TV client developers, some code for creating the device direct play profile is adapted from there
- Thanks to the Jellyfin Kotlin SDK developers for making it easier to interact with the Jellyfin server API
- Thanks to numerous other libraries that make app development even possible

## Additional screenshots

### Movie library browsing
<img width="1280" height="771" alt="0 1 0_02_movies" src="https://github.com/user-attachments/assets/0ea18f12-4b0a-49bd-b3c6-df45f97511f3" />

### Series page
<img width="1280" height="771" alt="0 1 0_03_series" src="https://github.com/user-attachments/assets/dc47cd0e-2cd2-4090-8f8e-3f776f67f465" />

### Playlist
<img width="1280" height="771" alt="0 1 0_04_playlist" src="https://github.com/user-attachments/assets/d33451cd-2be0-46c0-a7de-2064e2615d84" />
