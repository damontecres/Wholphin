# Dolphin developer's guide

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

You need a compatible Android Studio version for the configured AGP. This is generally `Narwhal 3 Feature Drop | 2025.1.3` or newer. See https://developer.android.com/build/releases/gradle-plugin and [`libs.versions.toml](./gradle/libs.versions.toml).
