package com.github.damontecres.dolphin.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class AppPreferencesSerializer
    @Inject
    constructor() : Serializer<AppPreferences> {
        override val defaultValue: AppPreferences =
            AppPreferences
                .newBuilder()
                .apply {
                    playbackPreferences =
                        PlaybackPreferences
                            .newBuilder()
                            .apply {
                                skipForwardMs =
                                    AppPreference.SkipForward.defaultValue.seconds.inWholeMilliseconds
                                skipBackMs =
                                    AppPreference.SkipBack.defaultValue.seconds.inWholeMilliseconds
                                controllerTimeoutMs = AppPreference.ControllerTimeout.defaultValue
                                seekBarSteps = AppPreference.SeekBarSteps.defaultValue.toInt()
                                showDebugInfo = AppPreference.PlaybackDebugInfo.defaultValue
                                autoPlayNext = AppPreference.AutoPlayNextUp.defaultValue
                                autoPlayNextDelaySeconds =
                                    AppPreference.AutoPlayNextDelay.defaultValue
                                skipBackOnResumeSeconds =
                                    AppPreference.SkipBackOnResume.defaultValue.seconds.inWholeMilliseconds
                            }.build()
                    homePagePreferences =
                        HomePagePreferences
                            .newBuilder()
                            .apply {
                                maxItemsPerRow = AppPreference.HomePageItems.defaultValue.toInt()
                                enableRewatchingNextUp = AppPreference.RewatchNextUp.defaultValue
                            }.build()
                    interfacePreferences =
                        InterfacePreferences
                            .newBuilder()
                            .apply {
                                playThemeSongs = AppPreference.PlayThemeMusic.defaultValue
                            }.build()
                }.build()

        override suspend fun readFrom(input: InputStream): AppPreferences {
            try {
                return AppPreferences.parseFrom(input)
            } catch (exception: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read proto.", exception)
            }
        }

        override suspend fun writeTo(
            t: AppPreferences,
            output: OutputStream,
        ) = t.writeTo(output)
    }

inline fun AppPreferences.update(block: AppPreferences.Builder.() -> Unit): AppPreferences = toBuilder().apply(block).build()

inline fun AppPreferences.updatePlaybackPreferences(block: PlaybackPreferences.Builder.() -> Unit): AppPreferences =
    update {
        playbackPreferences = playbackPreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateHomePagePreferences(block: HomePagePreferences.Builder.() -> Unit): AppPreferences =
    update {
        homePagePreferences = homePagePreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateInterfacePreferences(block: InterfacePreferences.Builder.() -> Unit): AppPreferences =
    update {
        interfacePreferences = interfacePreferences.toBuilder().apply(block).build()
    }

fun AppPreferences.rememberTab(
    itemId: UUID,
    index: Int,
): AppPreferences {
    Timber.v("Updating $itemId tab to $index")
    return updateInterfacePreferences {
        putRememberedTabs(itemId.toString(), index)
    }
}
