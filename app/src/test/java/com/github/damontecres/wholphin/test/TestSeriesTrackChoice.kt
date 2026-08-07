package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.SeriesTrackChoiceDao
import com.github.damontecres.wholphin.data.model.ActivationFlag
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.data.model.TrackChoiceParentType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.StreamChoiceReason
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.audio
import com.github.damontecres.wholphin.services.mockServerRepo
import com.github.damontecres.wholphin.services.subtitle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestSeriesTrackChoice {
    private val mockServerRepository =
        mockServerRepo(
            audioLang = "eng",
            subtitleLang = "eng",
            subtitleMode = SubtitlePlaybackMode.DEFAULT,
        )
    private val mockSeriesTrackChoiceDao = mockk<SeriesTrackChoiceDao>(relaxed = false)

    private val streamChoiceService =
        StreamChoiceService(mockServerRepository, mockSeriesTrackChoiceDao)

    private val seriesId = UUID.randomUUID()
    private val season1Id = UUID.randomUUID()
    private val season2Id = UUID.randomUUID()
    private val season3Id = UUID.randomUUID()

    private val user =
        JellyfinUser(
            1,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            null,
        )

    @Before
    fun before() {
        every { mockServerRepository.currentUser } returns user
    }

    @Test
    fun `Test StreamChoiceService call correct dao function`() =
        runTest {
            val mockSeriesTrackChoiceDao = mockk<SeriesTrackChoiceDao>(relaxed = true)
            val streamChoiceService =
                StreamChoiceService(mockServerRepository, mockSeriesTrackChoiceDao)

            val seriesId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()

            // Has both series & season
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = seriesId,
                parentId = seasonId,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.get(any(), seriesId = seriesId, seasonId = seasonId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }

            // Has only series
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = seriesId,
                parentId = null,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.getBySeriesId(any(), seriesId = seriesId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }

            // Has only season
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.EPISODE,
                seriesId = null,
                parentId = seasonId,
            ).let { item ->
                streamChoiceService.getSeriesTrackChoices(item, MediaStreamType.SUBTITLE)
                coVerify(exactly = 1) {
                    mockSeriesTrackChoiceDao.getBySeasonId(any(), seasonId = seasonId, type = SeriesTrackChoiceType.SUBTITLE)
                }
            }
        }

    @Test
    fun `Test series disabled`() =
        runTest {
            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback = null,
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            subtitle(0, "eng", default = true),
                            subtitle(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.DISABLED,
                            ),
                        ),
                )
            assertNull(result.stream)
        }

    @Test
    fun `Test series only forced`() =
        runTest {
            val mockServerRepository =
                mockServerRepo(
                    audioLang = "eng",
                    subtitleLang = "eng",
                    subtitleMode = SubtitlePlaybackMode.NONE,
                )
            val streamChoiceService =
                StreamChoiceService(mockServerRepository, mockSeriesTrackChoiceDao)

            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback = null,
                    prefs =
                        UserPreferences(
                            AppPreferences.getDefaultInstance(),
                            JellyfinUserPreferences(),
                        ),
                    candidates =
                        listOf(
                            subtitle(0, "eng", forced = true),
                            subtitle(1, "eng", forced = false),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.ONLY_FORCED,
                            ),
                        ),
                )
            assertNotNull(result.stream)
            assertEquals(0, result.stream!!.index)
            assertTrue(result.reason is StreamChoiceReason.Series)
        }

    @Test
    fun `Test series disabled with season`() =
        runTest {
            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback = null,
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            subtitle(0, "eng", default = true),
                            subtitle(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.DISABLED,
                            ),
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = season1Id,
                                parentType = TrackChoiceParentType.SEASON,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.DISABLED,
                            ),
                        ),
                )
            assertNull(result.stream)
            assertTrue(result.reason is StreamChoiceReason.Series)
        }

    @Test
    fun `Test item playback overrides STC for subtitles`() =
        runTest {
            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback =
                        ItemPlayback(
                            userId = user.rowId,
                            itemId = UUID.randomUUID(),
                            subtitleIndex = 1,
                        ),
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            subtitle(0, "eng", default = true),
                            subtitle(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.DISABLED,
                            ),
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = season1Id,
                                parentType = TrackChoiceParentType.SEASON,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.DISABLED,
                            ),
                        ),
                )
            assertNotNull(result.stream)
            assertEquals(1, result.stream!!.index)
            assertTrue(result.reason is StreamChoiceReason.Item)
        }

    @Test
    fun `Test item playback overrides STC for audio`() =
        runTest {
            val result =
                streamChoiceService.chooseAudioStream(
                    itemPlayback =
                        ItemPlayback(
                            userId = user.rowId,
                            itemId = UUID.randomUUID(),
                            audioIndex = 1,
                        ),
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            audio(0, "eng", default = true),
                            audio(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.AUDIO,
                                language = "eng",
                            ),
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = season1Id,
                                parentType = TrackChoiceParentType.SEASON,
                                type = SeriesTrackChoiceType.AUDIO,
                                language = "eng",
                            ),
                        ),
                )
            assertNotNull(result.stream)
            assertEquals(1, result.stream!!.index)
            assertTrue(result.reason is StreamChoiceReason.Item)
        }

    @Test
    fun `Test season STC doesn't apply but series STC does - subtitle`() =
        runTest {
            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback =
                        ItemPlayback(
                            userId = user.rowId,
                            itemId = UUID.randomUUID(),
                            audioIndex = 1,
                        ),
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            subtitle(0, "eng", default = true),
                            subtitle(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = season1Id,
                                parentType = TrackChoiceParentType.SEASON,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.ONLY_FORCED,
                            ),
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.SUBTITLE,
                                activation = ActivationFlag.ACTIVATED,
                                language = "spa",
                            ),
                        ),
                )
            assertNotNull(result.stream)
            assertEquals(1, result.stream!!.index)
            assertTrue(result.reason is StreamChoiceReason.Series)
        }

    @Test
    fun `Test season STC doesn't apply but series STC does - audio`() =
        runTest {
            val result =
                streamChoiceService.chooseSubtitleStream(
                    audioStreamLang = "eng",
                    itemPlayback = null,
                    prefs = UserPreferences(AppPreferences.getDefaultInstance(), null),
                    candidates =
                        listOf(
                            audio(0, "eng", default = true),
                            audio(1, "spa"),
                        ),
                    stc =
                        listOf(
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = season1Id,
                                parentType = TrackChoiceParentType.SEASON,
                                type = SeriesTrackChoiceType.AUDIO,
                                activation = ActivationFlag.ONLY_FORCED,
                            ),
                            SeriesTrackChoice(
                                userId = user.rowId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = SeriesTrackChoiceType.AUDIO,
                                activation = ActivationFlag.ACTIVATED,
                                language = "spa",
                            ),
                        ),
                )
            assertNotNull(result.stream)
            assertEquals(1, result.stream!!.index)
            assertTrue(result.reason is StreamChoiceReason.Series)
        }
}
