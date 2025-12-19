# Wholphin developer's guide

See also the [Contributing](CONTRIBUTING.md) guide for general information on contributing to the project.

##  Overview

This project is an Android TV client for Jellyfin. It is written in Kotlin and uses the official [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin) to interact with the server.

The app is a single Activity (`MainActivity`) with MVVM architecture.

The app uses:
* [Compose](https://developer.android.com/jetpack/compose) for the UI
* [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) for navigating app screen
* [Room](https://developer.android.com/training/data-storage/room) & [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for local data storage
* [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) for dependency injection
* [Media3/ExoPlayer](https://developer.android.com/media/media3/exoplayer) for media playback
* [Coil](https://coil-kt.github.io/coil/) for image loading
* [OkHttp](https://square.github.io/okhttp/) for HTTP requests

## Getting started

We follow GitHub's fork & pull request model for contributions.

After forking and cloning your fork, you can import the project into Android Studio.

### Development environment

It is recommended to use a recent version of [Android Studio](https://developer.android.com/studio). Make sure the [version is compatible](https://developer.android.com/build/releases/gradle-plugin#android_gradle_plugin_and_android_studio_compatibility) with Wholphin's AGP version.

Code formatting should follow [ktlint's](https://github.com/pinterest/ktlint) rules. Find the `ktlint` version in [`.pre-commit-config.yaml`](./.pre-commit-config.yaml). Optionally, install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) in Android Studio to run automatically. Configure the version in `Settings->Tools->KtLint->Ruleset Version`.

Also, it's recommend to add an extra ruleset jar for Compose-specific KtLint: https://mrmans0n.github.io/compose-rules/ktlint/#using-with-ktlint-cli-or-the-ktlint-intellij-plugin

Also setup [pre-commit](https://github.com/pre-commit/pre-commit) which will run `ktlint` as well on each commit, plus check for other common issues.

## Code organization

Code is split into several packages:
- `data` - app-specific data models and services
- `preferences` - Non-UI related code for user settings and preferences
- `services` - hilt injectable services often used by ViewModels for API calls
- `ui` - User interface code and ViewModels
- `util` - Utility classes and functions

### Native components

#### FFmpeg decoder module

Wholphin ships with [media3 ffmpeg decoder module](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md).

It is not required to build the extension in order to build the app locally.

You can build the module on MacOS or Linux with the [`build_ffmpeg_decoder.sh`](./scripts/ffmpeg/build_ffmpeg_decoder.sh) script.

#### MPV player backend

Wholphin has a playback engine that uses [`libmpv`](https://github.com/mpv-player/mpv). The app uses JNI code from [`mpv-android`](https://github.com/mpv-android/mpv-android) and has an implementation of `androidx.media3.common.Player` to swap out for `ExoPlayer`.

See the [build scripts](scripts/mpv/) for details on building this component.

### App settings

App settings are available with the `AppPreferences` object and defined by different `AppPreference` objects (note the `s` differences).

The `AppPreference` objects are used to create the UI for configuring settings using the composable functions in `com.github.damontecres.wholphin.ui.preferences`.

#### How to add a new app setting

1. Add entry in `WholphinDataStore.proto` & build to generate classes
2. Add new `AppPreference` object in `AppPreference.kt`
3. Add new object to a `PreferenceGroup` (listed in `AppPreference.kt`)
4. Update `AppPreferencesSerializer` to set the default value for new installs
5. If needed, update `AppUpgradeHandler` to set the default value for app upgrades
    - Since preferences use proto3, the [default values](https://protobuf.dev/programming-guides/proto3/#default) are zero, false, or the first enum, so only need this step if the default value is different
