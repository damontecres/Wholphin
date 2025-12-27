package com.github.damontecres.wholphin.test

import androidx.lifecycle.MutableLiveData
import com.github.damontecres.wholphin.data.PlaybackLanguageChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.StreamChoiceService
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TestStreamChoiceServiceBasic(
    val input: TestInput,
) {
    @Test
    fun test() {
        runTest(input)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<TestInput> =
            listOf(
                TestInput(
                    null,
                    SubtitlePlaybackMode.NONE,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.NONE,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    plc = plc(subtitleLang = "spa"),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    userSubtitleLang = "spa",
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    plc = plc(subtitlesDisabled = true),
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.DISABLED),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.UNSPECIFIED),
                ),
            )
    }
}

@RunWith(Parameterized::class)
class TestStreamChoiceServiceDefault(
    val input: TestInput,
) {
    @Test
    fun test() {
        runTest(input)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<TestInput> =
            listOf(
                TestInput(
                    0,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    userSubtitleLang = null,
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", true),
                        ),
                    userSubtitleLang = null,
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", true),
                        ),
                    userSubtitleLang = null,
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    itemPlayback = itemPlayback(subtitleIndex = 1),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "spa", false),
                        ),
                    plc = plc(subtitleLang = "spa"),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    plc = plc(subtitleLang = "spa"),
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    plc = plc(subtitlesDisabled = true),
                ),
            )
    }
}

@RunWith(Parameterized::class)
class TestStreamChoiceServiceSmart(
    val input: TestInput,
) {
    @Test
    fun test() {
        runTest(input)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<TestInput> =
            listOf(
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = null,
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "spa",
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "spa",
                    plc = plc(subtitleLang = "spa"),
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "spa",
                    plc = plc(subtitlesDisabled = true),
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                    userSubtitleLang = "spa",
                    userAudioLang = "spa",
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", true),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                    userSubtitleLang = "spa",
                    userAudioLang = "eng",
                ),
            )
    }
}

@RunWith(Parameterized::class)
class TestStreamChoiceServiceOnlyForced(
    val input: TestInput,
) {
    @Test
    fun test() {
        runTest(input)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<TestInput> =
            listOf(
                TestInput(
                    null,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = 1),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                    plc = plc(subtitleLang = "eng"),
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                    plc = plc(subtitlesDisabled = true),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "eng",
                ),
                TestInput(
                    1,
                    SubtitlePlaybackMode.ONLY_FORCED,
                    userSubtitleLang = null,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false),
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "eng",
                ),
            )
    }
}

data class TestInput(
    val expectedIndex: Int?,
    val userSubtitleMode: SubtitlePlaybackMode?,
    val userAudioLang: String? = "eng",
    val userSubtitleLang: String? = "eng",
    val streamAudioLang: String? = "eng",
    val subtitles: List<MediaStream>,
    val itemPlayback: ItemPlayback? = null,
    val plc: PlaybackLanguageChoice? = null,
) {
    override fun toString(): String = "test(mode=$userSubtitleMode, subtitles=${subtitles.map { it.toShortString() }})"
}

private fun MediaStream.toShortString(): String = "$type(index=$index, lang=$language, default=$isDefault, forced=$isForced)"

private fun serverRepo(
    audioLang: String?,
    subtitleMode: SubtitlePlaybackMode?,
    subtitleLang: String?,
): ServerRepository {
    val mocked = mockk<ServerRepository>()
    every { mocked.currentUserDto } returns
        MutableLiveData(
            UserDto(
                id = UUID.randomUUID(),
                hasPassword = true,
                hasConfiguredPassword = true,
                hasConfiguredEasyPassword = true,
                configuration =
                    DefaultUserConfiguration.copy(
                        audioLanguagePreference = audioLang,
                        subtitleMode = subtitleMode ?: SubtitlePlaybackMode.DEFAULT,
                        subtitleLanguagePreference = subtitleLang,
                    ),
            ),
        )
    return mocked
}

private fun runTest(input: TestInput) {
    val service =
        StreamChoiceService(
            serverRepo(input.userAudioLang, input.userSubtitleMode, input.userSubtitleLang),
            mockk<PlaybackLanguageChoiceDao>(),
        )
    val result =
        service.chooseSubtitleStream(
            audioStreamLang = input.streamAudioLang,
            candidates = input.subtitles,
            itemPlayback = input.itemPlayback,
            playbackLanguageChoice = input.plc,
            prefs = UserPreferences(AppPreferences.getDefaultInstance()),
        )
    Assert.assertEquals(input.expectedIndex, result?.index)
}

fun subtitle(
    index: Int,
    lang: String?,
    default: Boolean = false,
    forced: Boolean = false,
): MediaStream =
    MediaStream(
        type = MediaStreamType.SUBTITLE,
        language = lang,
        isDefault = default,
        isForced = forced,
        isHearingImpaired = false,
        isInterlaced = false,
        index = index,
        isExternal = false,
        isTextSubtitleStream = true,
        supportsExternalStream = true,
    )

private fun itemPlayback(
    audioIndex: Int = TrackIndex.UNSPECIFIED,
    subtitleIndex: Int = TrackIndex.UNSPECIFIED,
): ItemPlayback =
    ItemPlayback(
        rowId = 1,
        userId = 1,
        itemId = UUID.randomUUID(),
        sourceId = UUID.randomUUID(),
        audioIndex = audioIndex,
        subtitleIndex = subtitleIndex,
    )

private fun plc(
    audioLang: String? = null,
    subtitleLang: String? = null,
    subtitlesDisabled: Boolean? = if (subtitleLang != null) false else null,
): PlaybackLanguageChoice =
    PlaybackLanguageChoice(
        userId = 1,
        seriesId = UUID.randomUUID(),
        audioLanguage = audioLang,
        subtitleLanguage = subtitleLang,
        subtitlesDisabled = subtitlesDisabled,
    )
