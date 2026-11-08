# Volume leveling core

Plain Kotlin DSP used by `VolumeLevelingAudioProcessor`. It has no Android or Media3 dependencies, so it runs in JVM unit tests.

- `LoudnessMeter.kt`: BS.1770 loudness measurement (K-weighting filters, gated 400 ms blocks).
- `Api.kt`: `VolumeLeveler`, which moves each title toward a target loudness, compresses loud moments and limits peaks. `LevelingProfile` holds the tuning; the app uses `STRONG`.
- `DspMath.kt`: shared math helpers.

The processor calls `reset()` when a new item starts, so the next title is measured fresh instead of inheriting the previous title's gain.
