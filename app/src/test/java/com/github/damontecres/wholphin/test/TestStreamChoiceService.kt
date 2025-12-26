package com.github.damontecres.wholphin.test

import androidx.lifecycle.MutableLiveData
import com.github.damontecres.wholphin.data.PlaybackLanguageChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.DefaultUserConfiguration
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.StreamChoiceService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TestStreamChoiceService(
    val input: TestInput,
) {
    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var mockPlaybackLanguageChoiceDao: PlaybackLanguageChoiceDao

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

    @Test
    fun test() {
        val service =
            StreamChoiceService(
                serverRepo(input.userAudioLang, input.userSubtitleMode, input.userSubtitleLang),
                mockPlaybackLanguageChoiceDao,
            )
        val result =
            service.chooseSubtitleStream(
                audioStreamLang = input.streamAudioLang,
                candidates = input.subtitles,
                itemPlayback = null,
                playbackLanguageChoice = null,
                prefs = UserPreferences(AppPreferences.getDefaultInstance()),
            )
        Assert.assertEquals(input.expectedIndex, result?.index)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<TestInput> =
            listOf(
                // DEFAULT
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
                // SMART
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
                // ONLY_FORCED
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
) {
    override fun toString(): String = "test(mode=$userSubtitleMode, subtitles=$subtitles)"
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
