# Wholphin - an OSS Android TV client for Jellyfin

> "Never half-phin two jellies. Always wholphin one jelly."

Wholphin is an open-source Android TV client for Jellyfin. It aims to provide a different app UI that's inspired by Plex for users interested in migrating to Jellyfin.

This is not a fork of the [official client](https://github.com/jellyfin/jellyfin-androidtv). Wholphin's user interface and controls have been written completely from scratch. Wholphin `v0.3.0+` supports playing media using either ExoPlayer/Media3 or MPV (experimental).

<p align="center">
<a href="https://github.com/damontecres/Wholphin/releases">
<img alt="Current Release" src="https://img.shields.io/github/release/damontecres/wholphin.svg"/>
</a>
<a href="https://translate.codeberg.org/engage/wholphin/">
<img src="https://translate.codeberg.org/widget/wholphin/wholphin/svg-badge.svg" alt="Translation status" />
</a>
<br/>
<a href="https://play.google.com/store/apps/details?id=com.github.damontecres.wholphin">
<img width="180" alt="Get Wholphin on Google Play" src="https://github.com/user-attachments/assets/2550a4cb-ce46-47a1-ae24-f33a169234b7"/>
</a>
<a href="https://www.amazon.com/gp/product/B0G8RQQR9T/ref=mas_pm_wholphin">
<img width="180" alt="Get Wholphin on Amazon AppStore" src="https://github.com/user-attachments/assets/1f3a3b26-4b4f-44b1-9741-f4c895c8a53b"/>
</a>


</p>

<img width="1280" height="720" alt="0_3_5_home" src="https://github.com/user-attachments/assets/a485c015-ec21-442d-a757-1f18381bf799" />

## Features

### User interface

- A navigation drawer for quick access to libraries, favorites, search, and settings from almost anywhere in the app
- Integration with [Seerr](https://github.com/seerr-team/seerr) to discover new movies and TV shows
- Option to combine Continue Watching & Next Up rows
- Show Movie/TV Show titles when browsing libraries
- Play theme music, if available
- Customize subtitle style for plain text subtitles
- Search & download subtitles (requires compatible server plugin such as [OpenSubtitles](https://github.com/jellyfin/jellyfin-plugin-opensubtitles))
- Customize layout grids for libraries
- Multiple app color themes
- Protect user profile switches with PIN code

### Playback

- Different media playback engines:
  - **ExoPlayer** w/ optional extra audio & AV1 software decoding
  - **MPV** for direct playing anything plus ASS subtitle support
- Plex inspired playback controls:
  - Using D-Pad left/right for seeking during playback
  - Quickly access video chapters & queue during playback
  - Optionally skip back a few seconds when resuming playback
- Live TV & DVR support
- Auto play next episodes with pass out protection
- Option for automatic refresh rate & resolution switching on supported displays
- Trickplay support
- Subtly show playback position along the bottom of the screen while seeking w/ D-Pad


### Roadmap

See [here for the roadmap](https://github.com/damontecres/Wholphin/wiki#roadmap)

## Installation

Using [Google Play](https://play.google.com/store/apps/details?id=com.github.damontecres.wholphin) or [Amazon appstore](https://www.amazon.com/gp/product/B0G8RQQR9T/ref=mas_pm_wholphin) are the fastest way to install. But you can follow these instructions to install without needing an app store

Downloader Code: `8668671`

1. Enable side-loading "unknown" apps
    - https://androidtvnews.com/unknown-sources-chromecast-google-tv/
    - https://www.xda-developers.com/how-to-sideload-apps-android-tv/
    - https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources
    - https://www.aftvnews.com/how-to-enable-apps-from-unknown-sources-on-an-amazon-fire-tv-or-fire-tv-stick/
2. Install the APK on your Android TV device with one of these options:
    - Install a browser program such as [Downloader](https://www.aftvnews.com/downloader/), use it to get the latest apk with short code `8668671` or URL: http://aftv.news/8668671
    - Download the latest APK release from the [releases page](https://github.com/damontecres/Wholphin/releases/latest) or http://aftv.news/8668671
        - Put the APK on an SD Card/USB stick/network share and use a file manager app from the Google Play Store / Amazon AppStore (e.g. `FX File Explorer`). Android's preinstalled file manager probably will not work!
        - Use `Send files to TV` from the Google Play Store on your phone & TV
        - (Expert) Use [ADB](https://developer.android.com/studio/command-line/adb) to install the APK from your computer ([guide](https://fossbytes.com/side-load-apps-android-tv/#h-how-to-sideload-apps-on-your-android-tv-using-adb))

### Upgrading the app

After the initial install above, the app will automatically check for updates. The updates can be installed in settings.

The first time you attempt an update, the OS should guide you through enabling the required additional permissions for the app to install updates.

Note: if installed via an app store, the app store will handle updates.

## Compatibility

Requires Android 6+ (or Fire TV OS 6+) and Jellyfin server `10.10.x` or `10.11.x` (tested on primarily `10.11`).

The app is tested on a variety of Android TV/Fire TV OS devices, but if you encounter issues, please file an issue!

## Contributions

Issues and pull requests are always welcome! Please check before submitting that your issue or pull request is not a duplicate.

If you plan to contribute, please read the [contributing guide](CONTRIBUTING.md)!

You can [help translate Wholphin](https://translate.codeberg.org/engage/wholphin/)!

## Acknowledgements

- Thanks to the Jellyfin team for creating and maintaining such a great open-source media server
- Thanks to the official Jellyfin Android TV client developers, some code for creating the device direct play profile is adapted from there
- Thanks to the Jellyfin Kotlin SDK developers for making it easier to interact with the Jellyfin server API
- Thanks to numerous other libraries that make app development even possible

## Additional screenshots

### Movie library browsing
<img width="1280" height="771" alt="0 3 0_movies" src="https://github.com/user-attachments/assets/a49829b5-bc2c-4af9-8d5d-2f7d0973ce01" />

### Movie page
<img width="1280" height="720" alt="0_3_5_movie" src="https://github.com/user-attachments/assets/86af5889-6761-426a-8649-422f9d0a1dc0" />

### Series page
<img width="1280" height="720" alt="0_3_5_series" src="https://github.com/user-attachments/assets/2dcb2260-53ce-49d6-9088-72cbd4563c48" />

### Playlist
<img width="1280" height="771" alt="0 3 0_playlist" src="https://github.com/user-attachments/assets/7ca589ab-9c88-483a-b769-35ffb5663d9e" />
