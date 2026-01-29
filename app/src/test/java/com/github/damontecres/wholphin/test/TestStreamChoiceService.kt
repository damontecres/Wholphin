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
                TestInput(
                    1,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "spa", false),
                        ),
                    streamAudioLang = "eng",
                    userSubtitleLang = "spa",
                    userAudioLang = null,
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", false),
                            subtitle(1, "eng", true),
                            subtitle(2, "spa", false),
                        ),
                    streamAudioLang = "eng",
                    userSubtitleLang = "eng",
                    userAudioLang = null,
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng"),
                            subtitle(1, "spa"),
                        ),
                    streamAudioLang = "spa",
                    userSubtitleLang = "spa",
                    userAudioLang = null,
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

@RunWith(Parameterized::class)
class TestStreamChoiceServiceMultipleChoices(
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
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = true),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", false),
                        ),
                ),
                TestInput(
                    2,
                    SubtitlePlaybackMode.ALWAYS,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userAudioLang = null,
                ),
                TestInput(
                    2,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = null,
                    userAudioLang = null,
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = "spa",
                    userAudioLang = null,
                ),
                TestInput(
                    2,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = true),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = "eng",
                    userAudioLang = null,
                    streamAudioLang = "spa",
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = true),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = "eng",
                    userAudioLang = "eng",
                    streamAudioLang = "eng",
                ),
                TestInput(
                    null,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = "spa",
                    userAudioLang = "eng",
                    streamAudioLang = "spa",
                ),
                TestInput(
                    0,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "spa", forced = true, default = false),
                            subtitle(1, "spa", false),
                            subtitle(2, "spa", default = true),
                        ),
                    userSubtitleLang = "spa",
                    userAudioLang = "eng",
                    streamAudioLang = "eng",
                ),
                TestInput(
                    2,
                    SubtitlePlaybackMode.SMART,
                    subtitles =
                        listOf(
                            subtitle(0, "spa", forced = true, default = false),
                            subtitle(1, "spa", false),
                            subtitle(2, "spa", default = true),
                        ),
                    userSubtitleLang = "spa",
                    userAudioLang = "",
                    streamAudioLang = "eng",
                ),
                TestInput(
                    2,
                    SubtitlePlaybackMode.DEFAULT,
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true, default = false),
                            subtitle(1, "eng", false),
                            subtitle(2, "eng", default = true),
                        ),
                    userSubtitleLang = null,
                    userAudioLang = null,
                ),
            )
    }
}

/**
 * Tests for client-side "Only Forced" override (TrackIndex.ONLY_FORCED).
 * This tests the findForcedTrack function with user subtitle language preference.
 */
@RunWith(Parameterized::class)
class TestStreamChoiceServiceOnlyForcedClientOverride(
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
                // Test 1: Prefer user's subtitle language preference for forced tracks
                TestInput(
                    expectedIndex = 1, // spa forced track (matches user pref)
                    userSubtitleMode = null,
                    userSubtitleLang = "spa",
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 2: User subtitle preference matches Signs track via title (not forced flag)
                TestInput(
                    expectedIndex = 0, // eng signs track via title detection
                    userSubtitleMode = null,
                    userSubtitleLang = "eng",
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false, title = "Signs & Songs"),
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "jpn", // different from subtitle pref
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 3: Falls back to audio-matching forced when no preference match
                TestInput(
                    expectedIndex = 0, // eng forced (audio match, step 2)
                    userSubtitleMode = null,
                    userSubtitleLang = "spa", // no spa forced exists
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = true), // matches audio
                            subtitle(1, "und", forced = true),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 4: Falls back to audio-matching signs when user pref has no match
                TestInput(
                    expectedIndex = 0, // eng signs (audio match fallback)
                    userSubtitleMode = null,
                    userSubtitleLang = "fre", // user prefers French, no French forced exists
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false, title = "Signs & Songs"), // matches audio
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 5: Use audio language for signs/songs when NO subtitle preference
                TestInput(
                    expectedIndex = 0, // eng signs track matching audio
                    userSubtitleMode = null,
                    userSubtitleLang = null, // no preference
                    subtitles =
                        listOf(
                            subtitle(0, "eng", forced = false, title = "Signs & Songs"),
                            subtitle(1, "spa", forced = true),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 6: Unknown language forced track with no preference
                TestInput(
                    expectedIndex = 0, // unknown forced track
                    userSubtitleMode = null,
                    userSubtitleLang = null,
                    subtitles =
                        listOf(
                            subtitle(0, null, forced = true), // unknown/null language
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng",
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 7: Unknown language forced track when no audio match
                TestInput(
                    expectedIndex = 0, // unknown forced track (step 3)
                    userSubtitleMode = null,
                    userSubtitleLang = null,
                    subtitles =
                        listOf(
                            subtitle(0, "und", forced = true), // unknown language forced
                            subtitle(1, "spa", forced = false),
                        ),
                    streamAudioLang = "eng", // no eng tracks exist
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
                ),
                // Test 8: No matching forced track returns null (not irrelevant language)
                TestInput(
                    expectedIndex = null, // no forced track matches - return null instead of wrong language
                    userSubtitleMode = null,
                    userSubtitleLang = "eng",
                    subtitles =
                        listOf(
                            subtitle(0, "chi", forced = true), // Chinese forced - wrong language
                            subtitle(1, "spa", forced = true), // Spanish forced - wrong language
                        ),
                    streamAudioLang = "eng", // audio is English, no English forced
                    itemPlayback = itemPlayback(subtitleIndex = TrackIndex.ONLY_FORCED),
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
    title: String? = null,
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
        title = title,
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
